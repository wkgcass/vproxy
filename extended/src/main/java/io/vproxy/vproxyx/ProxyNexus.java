package io.vproxy.vproxyx;

import io.vproxy.base.component.elgroup.EventLoopGroup;
import io.vproxy.base.util.AnnotationKeys;
import io.vproxy.base.util.Annotations;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.Utils;
import io.vproxy.msquic.MsQuicUpcall;
import io.vproxy.msquic.MsQuicUtils;
import io.vproxy.msquic.QuicCertificateFile;
import io.vproxy.msquic.wrap.Configuration;
import io.vproxy.msquic.wrap.Listener;
import io.vproxy.pni.Allocator;
import io.vproxy.pni.array.IntArray;
import io.vproxy.vfd.IPPort;
import io.vproxy.vproxyx.nexus.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.vproxy.msquic.MsQuicConsts.*;

public class ProxyNexus {
    private static final String HELP_STR = """
        Usage:
                   -h|-help|--help|help                    Show this help message
                   node=<nodeName>                         Name of the current node
                   listen=<adminPort>                      Admin listening port
        [optional] load=<path>                             Load configuration from a local file
        [optional] server=<serverPort>                     Node listening port
        [optional] connect=<host:port>[,<host2:port2>]     Network addresses of nodes to connect to
                   certificate=<cert-pem-path>             Certificate used by the QUIC
                   privatekey=<key-pem-path>               Private key used by the QUIC
                   cacert=<ca-cert-pem-path>               Ca-certificate used by the QUIC
        Api:
            curl -X POST   /apis/v1.0/proxies --data '{"node":"{nodeName}", "target":"{host}:{port}", "listen":"{port}"}'
            curl -X GET    /apis/v1.0/proxies
            curl -X DELETE /apis/v1.0/proxies/{id}
        Configuration:
            {
                "proxies": [ {"node":"{nodeName}", "target":"{host}:{port}", "listen":"{port}"} ]
            }
        """;

    public static void main0(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println(HELP_STR);
            return;
        }
        String nodeName = null;
        int adminPort = 0;
        int serverPort = 0;
        String loadPath = null;
        var connect = new ArrayList<IPPort>();
        var certificatePath = "";
        var privateKeyPath = "";
        var cacertPath = "";
        for (var arg : args) {
            if (arg.equals("-h") || arg.equals("--help") || arg.equals("-help") || arg.equals("help")) {
                System.out.println(HELP_STR);
                return;
            }
            if (arg.startsWith("node=")) {
                var value = arg.substring("node=".length()).trim();
                if (value.isEmpty()) {
                    throw new IllegalArgumentException("node name is an empty string");
                }
                nodeName = value;
            } else if (arg.startsWith("listen=")) {
                var value = arg.substring("listen=".length()).trim();
                if (!Utils.isPortInteger(value)) {
                    throw new IllegalArgumentException("listen=" + value + " is not a valid port");
                }
                adminPort = Integer.parseInt(value);
            } else if (arg.startsWith("load=")) {
                var value = arg.substring("load=".length()).trim();
                if (value.isEmpty()) {
                    throw new IllegalArgumentException("load=`...` is an empty string");
                }
                loadPath = value;
            } else if (arg.startsWith("server=")) {
                var value = arg.substring("server=".length()).trim();
                if (!Utils.isPortInteger(value)) {
                    throw new IllegalArgumentException("server=" + value + " is not a valid port");
                }
                serverPort = Integer.parseInt(value);
            } else if (arg.startsWith("connect=")) {
                var value = arg.substring("connect=".length()).trim();
                var connectSplit = value.split(",");
                for (var s : connectSplit) {
                    if (!IPPort.validL4AddrStr(s)) {
                        throw new IllegalArgumentException(s + " is not valid ipport in `connect`");
                    }
                    var ipport = new IPPort(s);
                    if (connect.contains(ipport)) {
                        throw new IllegalArgumentException(s + " is already specified in `connect`");
                    }
                    connect.add(ipport);
                }
            } else if (arg.startsWith("certificate=")) {
                certificatePath = arg.substring("certificate=".length()).trim();
            } else if (arg.startsWith("privatekey=")) {
                privateKeyPath = arg.substring("privatekey=".length()).trim();
            } else if (arg.startsWith("cacert=")) {
                cacertPath = arg.substring("cacert=".length()).trim();
            } else {
                throw new IllegalArgumentException("unknown argument: " + arg);
            }
        }
        if (nodeName == null) {
            throw new IllegalArgumentException("node name is not specified");
        }
        if (NexusUtils.isNotValidNodeName(nodeName)) {
            throw new IllegalArgumentException("node name is not valid: " + nodeName);
        }
        if (adminPort == 0) {
            throw new IllegalArgumentException("`listen=...` is not specified");
        }
        if (loadPath != null) {
            loadPath = Utils.filename(loadPath);
            if (!new File(loadPath).isFile())
                throw new IllegalArgumentException("`load=" + loadPath + "` is not a file");
        }
        if (serverPort == 0 && connect.isEmpty()) {
            throw new IllegalArgumentException("at least one of `server=` or `connect=` should be specified");
        }
        if (certificatePath.isEmpty())
            throw new IllegalArgumentException("`certificate=` is not specified");
        if (privateKeyPath.isEmpty())
            throw new IllegalArgumentException("`privatekey=` is not specified");
        certificatePath = Utils.filename(certificatePath);
        privateKeyPath = Utils.filename(privateKeyPath);
        if (cacertPath.isEmpty())
            throw new IllegalArgumentException("`cacert=` is not specified");
        cacertPath = Utils.filename(cacertPath);

        var tmpAllocator = Allocator.ofConfined();
        var retCode = new IntArray(tmpAllocator, 1);

        var elg = new EventLoopGroup("worker-elg", new Annotations(Map.of(
            AnnotationKeys.EventLoopGroup_UseMsQuic.name, "true"
        )));
        var reg = elg.getMsquicRegistration();
        var loop = elg.list().getFirst();

        var alpnBuffers = MsQuicUtils.newAlpnBuffers(List.of(NexusContext.ALPN), tmpAllocator);
        var quicSettings = MsQuicUtils.newSettings(NexusContext.GENERAL_TIMEOUT, tmpAllocator);
        Configuration clientConf = null;
        Configuration serverConf = null;
        for (int i = 0; i < 2; ++i) {
            var clientConfAllocator = Allocator.ofUnsafe();
            var confQ = reg.opts.registrationQ.openConfiguration(alpnBuffers, 1,
                quicSettings, null, retCode, clientConfAllocator);
            if (retCode.get(0) != 0) {
                throw new IllegalArgumentException("unable to open quic configuration, error code: " + retCode.get(0));
            }
            var conf = new Configuration(new Configuration.Options(reg, confQ, clientConfAllocator));
            if (i == 0) {
                clientConf = conf;
            } else {
                serverConf = conf;
            }
        }
        if (serverPort > 0) {
            var credential = MsQuicUtils.newServerCredential(certificatePath, privateKeyPath, tmpAllocator);
            credential.setFlags(credential.getFlags()
                                | QUIC_CREDENTIAL_FLAG_REQUIRE_CLIENT_AUTHENTICATION
                                | QUIC_CREDENTIAL_FLAG_SET_CA_CERTIFICATE_FILE
                                | QUIC_CREDENTIAL_FLAG_USE_TLS_BUILTIN_CERTIFICATE_VALIDATION);
            credential.setCaCertificateFile(cacertPath, tmpAllocator);
            int err = serverConf.opts.configurationQ.loadCredential(credential);
            if (err != 0) {
                throw new IllegalArgumentException("unable to load credential, error code: " + err);
            }
        } else {
            serverConf.close();
            serverConf = null;
        }
        if (!connect.isEmpty()) {
            var credential = MsQuicUtils.newClientCredential(false, cacertPath, tmpAllocator);
            credential.setType(QUIC_CREDENTIAL_TYPE_CERTIFICATE_FILE);
            credential.setFlags(credential.getFlags() | QUIC_CREDENTIAL_TYPE_CERTIFICATE_FILE);
            var cf = new QuicCertificateFile(tmpAllocator);
            cf.setCertificateFile(certificatePath, tmpAllocator);
            cf.setPrivateKeyFile(privateKeyPath, tmpAllocator);
            credential.getCertificate().setCertificateFile(cf);
            int err = clientConf.opts.configurationQ.loadCredential(credential);
            if (err != 0) {
                throw new IllegalArgumentException("unable to load credential, error code: " + err);
            }
        } else {
            clientConf.close();
            clientConf = null;
        }

        var nexus = new Nexus();
        var resources = new ResHolder();
        var nctx = new NexusContext(nodeName, nexus, resources, loop, reg, clientConf, serverConf);

        var self = new NexusNode(nodeName, null);
        nexus.setSelfNode(self);
        for (var target : connect) {
            var peer = NexusPeer.create(nctx, target);
            peer.start();
        }

        if (serverPort > 0) {
            var listenerAllocator = Allocator.ofUnsafe();
            var lsn = new Listener(new Listener.Options(reg, listenerAllocator, new NexusQuicListenerCallback(nctx),
                ref -> reg.opts.registrationQ.openListener(
                    MsQuicUpcall.listenerCallback, ref.MEMORY, retCode, listenerAllocator
                )));
            if (retCode.get(0) != 0) {
                throw new Exception("failed to create quic listener, error code: " + retCode.get(0));
            }
            var err = lsn.start(new IPPort("0.0.0.0", serverPort), NexusContext.ALPN);
            if (err != 0) {
                throw new Exception("failed to start quic listener, error code: " + err);
            }
            Logger.alert("quic server listens on port " + serverPort);
        }

        tmpAllocator.close();

        var adminServer = new AdminServer(nctx, adminPort);
        adminServer.start();
        Logger.alert("admin server listens on port " + adminPort);
        if (loadPath == null) {
            Logger.alert("no configuration will be loaded");
        } else {
            adminServer.loadConfigOrExit(loadPath);
        }
        Logger.alert("proxy nexus launched");
    }
}

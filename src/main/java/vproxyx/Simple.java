package vproxyx;

import vproxy.app.Application;
import vproxy.app.Config;
import vproxy.app.Main;
import vproxy.component.app.Shutdown;
import vproxy.component.check.HealthCheckConfig;
import vproxy.component.secure.SecurityGroup;
import vproxy.component.ssl.CertKey;
import vproxy.component.svrgroup.Method;
import vproxy.component.svrgroup.ServerGroup;
import vproxy.util.Utils;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class Simple {
    private static final List<String> supportedProtocols = Arrays.asList(
        "tcp", "http", "h2", "http/1.x", "framed-int32", "dubbo"
    );
    private static final String supportedProtocolsStr;

    static {
        StringBuilder sb = new StringBuilder();
        sb.append(supportedProtocols.get(0));
        for (int i = 1; i < supportedProtocols.size(); ++i) {
            sb.append("|").append(supportedProtocols.get(i));
        }
        supportedProtocolsStr = sb.toString();
    }

    private static final String _HELP_STR_ = "" +
        "vproxy simple mode usage: java -Deploy=Simple " + Main.class.getName() + " \\" +
        "\n\t\tbind {port}                                          The lb listening port" +
        "\n" +
        "\n\t\tbackend {host1:port1,host2:port2}                    The proxy destination address:port" +
        "\n\t\t                                                     Multiple backends are separated with `,`" +
        "\n\t\thelp                                      [Optional] Show this message and exit" +
        "\n" +
        "\n\t\tssl {cert1-path,cert2-path} {key-path}    [Optional] SSL certificate and key file path (pem format)" +
        "\n\t\t                                                     Multiple certificates are separated with `,`" +
        "\n\t\tprotocol {" + supportedProtocolsStr + "}" +
        "\n\t\t                                          [Optional] The forward protocol, default tcp" +
        "\n\t\tgen                                       [Optional] Show config and exit" +
        "";

    public static void main0(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println(_HELP_STR_);
            System.exit(0);
            return;
        }

        int port = -1;
        String backend = null;
        String[] certpath = null;
        String keypath = null;
        String protocol = "tcp";
        boolean gen = false;
        // read args
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            String next = args.length > i + 1 ? args[i + 1].trim() : null;
            String next2 = args.length > i + 2 ? args[i + 2].trim() : null;
            if (next != null && next.isBlank()) {
                next = null;
            }
            if (next2 != null && next2.isBlank()) {
                next2 = null;
            }

            switch (arg) {
                case "help":
                    System.out.println(_HELP_STR_);
                    System.exit(0);
                    return;
                case "bind":
                    if (next == null) {
                        throw new Exception("missing port in `bind {port}`");
                    }
                    try {
                        port = Integer.parseInt(next);
                    } catch (NumberFormatException e) {
                        throw new Exception("port is not a number in `bind {port}`");
                    }
                    i += 1;
                    break;
                case "backend":
                    if (next == null) {
                        throw new Exception("missing address in `backend {host:port}`");
                    }
                    backend = next;
                    i += 1;
                    break;
                case "ssl":
                    if (next == null) {
                        throw new Exception("missing cert path in `ssl {cert} {key}`");
                    }
                    if (next2 == null) {
                        throw new Exception("missing key path in `ssl {cert} {key}`");
                    }
                    certpath = next.split(",");
                    keypath = next2;
                    i += 2;
                    break;
                case "protocol":
                    if (next == null) {
                        throw new Exception("missing protocol value in `protocol {...}`");
                    }
                    protocol = next;
                    i += 1;
                    break;
                case "gen":
                    gen = true;
                    break;
                default:
                    throw new Exception("unknown parameter " + arg);
            }
        }
        // check args exist
        if (port == -1) {
            throw new Exception("use `bind {port}` to set the listening port");
        }
        if (backend == null) {
            throw new Exception("use `backend {host:port}` to set the backend list");
        }
        // check args value
        if (port < 1 || port > 65535) {
            throw new Exception("invalid port range in `bind {port}`: " + port);
        }
        class HostPort {
            final String host;
            final int port;

            HostPort(String host, int port) {
                this.host = host;
                this.port = port;
            }
        }
        List<HostPort> backendList = new LinkedList<>();
        {
            String[] backends = backend.split(",");
            for (String b : backends) {
                String[] foo = b.split(":");
                if (foo.length != 2 || foo[0].isBlank() || foo[1].isBlank()) {
                    throw new Exception("invalid address format in `backend {host:port}`");
                }
                String host = foo[0];
                int dport;
                try {
                    dport = Integer.parseInt(foo[1]);
                } catch (NumberFormatException e) {
                    throw new Exception("invalid port format in `backend {host:port}`: " + b);
                }
                if (dport < 1 || dport > 65535) {
                    throw new Exception("invalid port range in `backend {host:port}`: " + b);
                }
                backendList.add(new HostPort(host, dport));
            }
        }
        if (!supportedProtocols.contains(protocol)) {
            throw new Exception("unsupported protocol in `protocol {...}`: " + protocol);
        }

        // start
        Config.configLoadingDisabled = true;
        Config.configSavingDisabled = true;
        Config.configModifyDisabled = true;

        // load key
        if (certpath != null) {
            Application.get().certKeyHolder.add("crt", certpath, keypath);
        }
        // create group
        Application.get().upstreamHolder.add("collection");
        ServerGroup group = Application.get().serverGroupHolder.add("backend",
            Application.get().eventLoopGroupHolder.get(Application.DEFAULT_WORKER_EVENT_LOOP_GROUP_NAME),
            new HealthCheckConfig(1000, 5000, 2, 3), Method.wrr);
        int svrCnt = 0;
        for (HostPort svr : backendList) {
            if (Utils.isIpLiteral(svr.host)) {
                group.add("backend" + (++svrCnt), new InetSocketAddress(InetAddress.getByName(svr.host), svr.port), 10);
            } else {
                InetAddress addr;
                try {
                    addr = InetAddress.getByName(svr.host);
                } catch (UnknownHostException e) {
                    throw new Exception("unknown host in `backend {host:port}`: " + svr.host);
                }
                group.add("backend" + (++svrCnt), svr.host, new InetSocketAddress(addr, svr.port), 10);
            }
        }
        Application.get().upstreamHolder.get("collection").add(group, 10);
        // create lb
        Application.get().tcpLBHolder.add("loadbalancer",
            Application.get().eventLoopGroupHolder.get(Application.DEFAULT_ACCEPTOR_EVENT_LOOP_GROUP_NAME),
            Application.get().eventLoopGroupHolder.get(Application.DEFAULT_WORKER_EVENT_LOOP_GROUP_NAME),
            new InetSocketAddress(InetAddress.getByAddress(new byte[]{0, 0, 0, 0}), port),
            Application.get().upstreamHolder.get("collection"),
            60_000,
            4096,
            4096,
            protocol,
            certpath == null ? null : new CertKey[]{Application.get().certKeyHolder.get("crt")},
            SecurityGroup.allowAll());
        // done

        System.err.print(Shutdown.currentConfig());

        // check for `gen`
        if (gen) {
            System.out.println("Config is printed in stderr");
            System.exit(0);
            return;
        }

        // show that everything is running
        System.out.println("launched");
    }
}

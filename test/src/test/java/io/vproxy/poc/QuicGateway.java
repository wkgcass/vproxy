package io.vproxy.poc;

import io.vproxy.base.Config;
import io.vproxy.base.component.elgroup.EventLoopGroup;
import io.vproxy.base.connection.NetEventLoop;
import io.vproxy.base.connection.ServerSock;
import io.vproxy.base.selector.wrap.quic.QuicServerSocketFD;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.exception.XException;
import io.vproxy.component.proxy.Proxy;
import io.vproxy.component.proxy.ProxyEventHandler;
import io.vproxy.component.proxy.ProxyNetConfig;
import io.vproxy.component.secure.SecurityGroup;
import io.vproxy.component.ssl.CertKey;
import io.vproxy.component.svrgroup.Upstream;
import io.vproxy.msquic.*;
import io.vproxy.msquic.callback.ConnectionCallback;
import io.vproxy.msquic.callback.ConnectionCallbackList;
import io.vproxy.msquic.callback.ListenerCallback;
import io.vproxy.msquic.wrap.Configuration;
import io.vproxy.msquic.wrap.Connection;
import io.vproxy.msquic.wrap.Listener;
import io.vproxy.pni.Allocator;
import io.vproxy.pni.PooledAllocator;
import io.vproxy.pni.array.IntArray;
import io.vproxy.vfd.IPPort;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static io.vproxy.msquic.MsQuicConsts.*;

public class QuicGateway {
    public final String alias;
    public final EventLoopGroup workerGroup;
    public final IPPort bindAddress;
    public final Upstream backend;
    public final int timeout;
    private int inBufferSize; // modifiable
    private int outBufferSize; // modifiable
    public final List<String> alpn;
    private final CertKey certKey;
    public SecurityGroup securityGroup;

    public QuicGateway(String alias,
                       EventLoopGroup workerGroup,
                       IPPort bindAddress,
                       Upstream backend,
                       int timeout,
                       int inBufferSize, int outBufferSize,
                       List<String> alpn,
                       CertKey certKey,
                       SecurityGroup securityGroup) throws Exception {
        this.alias = alias;
        this.workerGroup = workerGroup;
        this.bindAddress = bindAddress;
        this.backend = backend;
        this.timeout = timeout;
        this.inBufferSize = inBufferSize;
        this.outBufferSize = outBufferSize;
        this.alpn = Collections.unmodifiableList(alpn);
        this.certKey = certKey;
        this.securityGroup = securityGroup;

        assert Logger.lowLevelDebug("starting quic gateway");

        // check for binding
        if (Config.checkBind) {
            ServerSock.checkBindUDP(this.bindAddress);
        }
        // check event loop
        if (!workerGroup.annotations.EventLoopGroup_UseMsQuic) {
            throw new XException("event loop group " + workerGroup.alias + " does not support msquic");
        }
    }

    private volatile boolean isStarted;
    private volatile boolean pendingCleanup;
    private Configuration conf;
    private Listener lsn;

    public void start() throws IOException {
        if (isStarted) {
            return;
        }
        synchronized (this) {
            if (isStarted) {
                return;
            }
            isStarted = true;
        }
        if (pendingCleanup) {
            return;
        }
        start0();
    }

    private void start0() throws IOException {
        var reg = workerGroup.getMsquicRegistration();
        try (var tmpAlloc = Allocator.ofConfined()) {
            var retInt = new IntArray(tmpAlloc, 1);
            assert Logger.lowLevelDebug("before init msquic configuration");
            QuicBuffer.Array alpnBuffers;
            {
                var settings = new QuicSettings(tmpAlloc);
                {
                    settings.getIsSet().setIdleTimeoutMs(true);
                    settings.setIdleTimeoutMs(timeout);
                    settings.getIsSet().setCongestionControlAlgorithm(true);
                    settings.setCongestionControlAlgorithm((short) QUIC_CONGESTION_CONTROL_ALGORITHM_BBR);
                    settings.getIsSet().setServerResumptionLevel(true);
                    settings.setServerResumptionLevel((byte) QUIC_SERVER_RESUME_AND_ZERORTT);
                    settings.getIsSet().setPeerBidiStreamCount(true);
                    settings.setPeerBidiStreamCount((short) 4096);
                }
                alpnBuffers = MsQuicUtils.newAlpnBuffers(alpn, tmpAlloc);
                var confAllocator = PooledAllocator.ofUnsafePooled();
                var conf_ = reg.opts.registrationQ
                    .openConfiguration(alpnBuffers, alpn.size(), settings, null, retInt, confAllocator);
                if (conf_ == null) {
                    confAllocator.close();
                    throw new IOException("ConfigurationOpen failed: " + retInt.get(0));
                }
                conf = new Configuration(new Configuration.Options(reg, conf_, confAllocator));
                var cred = new QuicCredentialConfig(tmpAlloc);
                var tup = certKey.ensureCertKeyFile();
                var certFile = tup.left;
                var keyFile = tup.right;
                {
                    cred.setType(QUIC_CREDENTIAL_TYPE_CERTIFICATE_FILE);
                    cred.setFlags(QUIC_CREDENTIAL_FLAG_NONE);
                    var cf = new QuicCertificateFile(tmpAlloc);
                    cf.setCertificateFile(certFile, tmpAlloc);
                    cf.setPrivateKeyFile(keyFile, tmpAlloc);
                    cred.getCertificate().setCertificateFile(cf);
                    cred.setAllowedCipherSuites(
                        QUIC_ALLOWED_CIPHER_SUITE_AES_128_GCM_SHA256 |
                        QUIC_ALLOWED_CIPHER_SUITE_AES_256_GCM_SHA384 |
                        QUIC_ALLOWED_CIPHER_SUITE_CHACHA20_POLY1305_SHA256
                    );
                }
                int err = conf.opts.configurationQ.loadCredential(cred);
                if (err != 0) {
                    conf.close();
                    throw new IOException("ConfigurationLoadCredential failed");
                }
            }
            assert Logger.lowLevelDebug("before init msquic listener");
            {
                var listenerAllocator = PooledAllocator.ofUnsafePooled();
                var lsn = new Listener(new Listener.Options(reg, listenerAllocator, new QuicGatewayListenerCallback(), ref ->
                    reg.opts.registrationQ.openListener(MsQuicUpcall.listenerCallback, ref.MEMORY, null, listenerAllocator)));
                if (lsn.listenerQ == null) {
                    lsn.close();
                    throw new IOException("failed creating listener");
                }
                var err = lsn.start(bindAddress, alpn);
                if (err != 0) {
                    lsn.close();
                    throw new IOException("ListenerStart failed");
                }
                this.lsn = lsn;
            }
        } catch (IOException e) {
            isStarted = false;
            cleanup();
            throw e;
        }
    }

    public void stop() {
        isStarted = false;
        cleanup();
    }

    public void destroy() {
        stop();
    }

    private void cleanup() {
        if (pendingCleanup) {
            return;
        }
        synchronized (this) {
            if (pendingCleanup) {
                return;
            }
            pendingCleanup = true;
        }
        if (lsn != null) {
            lsn.close();
            lsn = null;
            return; // close conf in callback
        }
        cleanup2();
    }

    private void cleanup2() {
        // this method must be called after listener is ensured to be closed
        if (conf != null) {
            conf.close();
            conf = null;
        }
        pendingCleanup = false;
        if (isStarted) {
            // need to start again
            try {
                start0();
            } catch (Exception e) {
                Logger.error(LogType.SYS_ERROR, "failed to start quic-gateway " + alias + " again after cleanup", e);
            }
        }
    }

    private class QuicGatewayListenerCallback implements ListenerCallback {
        @Override
        public int newConnection(Listener listener, QuicListenerEventNewConnection data) {
            var connHQUIC = data.getConnection();

            QuicServerSocketFD fd;
            ServerSock s;
            try {
                fd = QuicServerSocketFD.wrapAcceptedConnection(Logger.debugOn(), listener, conf, connHQUIC);
                s = ServerSock.wrap(fd, bindAddress, new ServerSock.BindOptions());
            } catch (IOException e) {
                Logger.error(LogType.SYS_ERROR, "failed to init QuicServerSocketFD", e);
                return 0;
            }

            assert Logger.lowLevelDebug(STR."accepted new quic connection \{s}");

            var currentLoop = NetEventLoop.current();
            //noinspection Convert2Lambda,Anonymous2MethodRef
            var proxy = new Proxy(
                new ProxyNetConfig()
                    .setAcceptLoop(currentLoop)
                    .setServer(s)
                    .setConnGen(backend)
                    .setTimeout(timeout)
                    .setInBufferSize(inBufferSize)
                    .setOutBufferSize(outBufferSize)
                    .setHandleLoopProvider(_ -> currentLoop),
                new ProxyEventHandler() {
                    @Override
                    public void serverRemoved(ServerSock server) {
                        server.close();
                    }
                });

            ((ConnectionCallbackList) fd.getConnection().opts.callback).add(new ConnectionCallback() {
                @Override
                public int shutdownComplete(Connection conn, QuicConnectionEventConnectionShutdownComplete data) {
                    proxy.stop();
                    s.close();
                    return 0;
                }
            });

            try {
                proxy.handle();
            } catch (IOException e) {
                Logger.error(LogType.SYS_ERROR, STR."failed to start proxy for QuicGateway:\{alias}");
                s.close();
                return 0;
            }
            return 0;
        }

        @Override
        public int stopComplete(Listener listener, QuicListenerEventStopComplete data) {
            Logger.warn(LogType.ALERT, STR."quic listener \{bindAddress} stopped");
            cleanup2();
            return 0;
        }
    }

    public CertKey getCertKey() {
        return certKey;
    }

    public int getInBufferSize() {
        return inBufferSize;
    }

    public void setInBufferSize(int inBufferSize) {
        this.inBufferSize = inBufferSize;
    }

    public int getOutBufferSize() {
        return outBufferSize;
    }

    public void setOutBufferSize(int outBufferSize) {
        this.outBufferSize = outBufferSize;
    }

    @Override
    public String toString() {
        return alias + " -> worker " + workerGroup.alias +
               " bind " + bindAddress.formatToIPPortString() +
               " backend " + backend.alias + " cert-key " + getCertKey().alias +
               " timeout " + timeout +
               " in-buffer-size " + getInBufferSize() +
               " out-buffer-size " + getOutBufferSize() +
               " alpn " + String.join(",", alpn) +
               " security-group " + securityGroup.alias;
    }
}

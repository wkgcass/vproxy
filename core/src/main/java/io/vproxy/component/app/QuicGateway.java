package io.vproxy.component.app;

import io.vproxy.base.Config;
import io.vproxy.base.component.elgroup.EventLoopGroup;
import io.vproxy.base.connection.*;
import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.RingBuffer;
import io.vproxy.base.util.exception.XException;
import io.vproxy.base.util.nio.ByteArrayChannel;
import io.vproxy.base.util.ringbuffer.SimpleRingBuffer;
import io.vproxy.base.util.ringbuffer.SimpleRingBufferReaderCommitter;
import io.vproxy.component.secure.SecurityGroup;
import io.vproxy.component.ssl.CertKey;
import io.vproxy.component.svrgroup.Upstream;
import io.vproxy.msquic.*;
import io.vproxy.msquic.wrap.Configuration;
import io.vproxy.msquic.wrap.Connection;
import io.vproxy.msquic.wrap.Listener;
import io.vproxy.msquic.wrap.Stream;
import io.vproxy.pni.Allocator;
import io.vproxy.pni.PNIString;
import io.vproxy.pni.array.IntArray;
import io.vproxy.vfd.IPPort;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.Arrays;
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

    public void start() throws XException, IOException {
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

    private void start0() throws XException, IOException {
        var reg = workerGroup.getMsquicRegistration();
        try (var tmpAlloc = Allocator.ofConfined()) {
            var retInt = new IntArray(tmpAlloc, 1);
            assert Logger.lowLevelDebug("before init msquic configuration");
            {
                var settings = new QuicSettings(tmpAlloc);
                {
                    settings.getIsSet().setIdleTimeoutMs(1);
                    settings.setIdleTimeoutMs(timeout);
                    settings.getIsSet().setCongestionControlAlgorithm(1);
                    settings.setCongestionControlAlgorithm((short) QUIC_CONGESTION_CONTROL_ALGORITHM_BBR);
                    settings.getIsSet().setServerResumptionLevel(1);
                    settings.setServerResumptionLevel((byte) QUIC_SERVER_RESUME_AND_ZERORTT);
                    settings.getIsSet().setPeerBidiStreamCount(1);
                    settings.setPeerBidiStreamCount((short) 4096);
                }
                var alpnBuffers = new QuicBuffer.Array(tmpAlloc, alpn.size());
                for (int i = 0; i < alpn.size(); i++) {
                    var a = alpn.get(i);
                    var str = new PNIString(tmpAlloc, a);
                    alpnBuffers.get(i).setBuffer(str.MEMORY);
                    alpnBuffers.get(i).setLength((int) (str.MEMORY.byteSize() - 1));
                }
                var confAllocator = Allocator.ofUnsafe();
                var conf_ = reg.opts.registrationQ
                    .openConfiguration(alpnBuffers, alpn.size(), settings, null, retInt, confAllocator);
                if (conf_ == null) {
                    confAllocator.close();
                    throw new XException("ConfigurationOpen failed: " + retInt.get(0));
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
                    throw new XException("ConfigurationLoadCredential failed");
                }
            }
            assert Logger.lowLevelDebug("before init msquic listener");
            {
                var listenerAllocator = Allocator.ofUnsafe();
                var lsn = new QuicGatewayListener(new Listener.Options(reg, listenerAllocator, ref ->
                    reg.opts.registrationQ.openListener(MsQuicUpcall.listenerCallback, ref.MEMORY, null, listenerAllocator)));
                if (lsn.listenerQ == null) {
                    listenerAllocator.close();
                    throw new RuntimeException("failed creating listener");
                }
                var alpnBuffers = new QuicBuffer.Array(listenerAllocator, 2);
                alpnBuffers.get(0).setBuffer(new PNIString(listenerAllocator, "proto-x").MEMORY);
                alpnBuffers.get(0).setLength(7);
                alpnBuffers.get(1).setBuffer(new PNIString(listenerAllocator, "proto-y").MEMORY);
                alpnBuffers.get(1).setLength(7);
                var quicAddr = new QuicAddr(listenerAllocator);
                MsQuic.get().buildQuicAddr(
                    new PNIString(listenerAllocator, bindAddress.getAddress().formatToIPString()),
                    bindAddress.getPort(),
                    quicAddr);
                var err = lsn.listenerQ.start(alpnBuffers, 2, quicAddr);
                if (err != 0) {
                    lsn.close();
                    throw new RuntimeException("ListenerStart failed");
                }
                this.lsn = lsn;
            }
        } catch (XException | IOException e) {
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
            lsn.closeListener();
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

    private class QuicGatewayListener extends Listener {
        public QuicGatewayListener(Options opts) {
            super(opts);
        }

        @Override
        public int callback(QuicListenerEvent event) {
            int ret = super.callback(event);
            return switch (event.getType()) {
                case QUIC_LISTENER_EVENT_NEW_CONNECTION -> {
                    var data = event.getUnion().getNEW_CONNECTION();
                    var connHQUIC = data.getConnection();
                    var connectionAllocator = Allocator.ofUnsafe();
                    var conn_ = new QuicConnection(connectionAllocator);
                    {
                        conn_.setApi(opts.apiTableQ.getApi());
                        conn_.setConn(connHQUIC);
                    }
                    var conn = new QuicGatewayConnection(new Connection.Options(this, connectionAllocator, conn_));
                    opts.apiTableQ.setCallbackHandler(connHQUIC, MsQuicUpcall.connectionCallback, conn.ref.MEMORY);
                    var err = conn_.setConfiguration(conf.opts.configurationQ);
                    if (err != 0) {
                        Logger.error(LogType.CONN_ERROR, "set configuration to connection failed: " + err);
                        conn.close();
                        yield err;
                    }
                    assert Logger.lowLevelDebug("accepted new quic connection " + conn);
                    yield 0;
                }
                case QUIC_LISTENER_EVENT_STOP_COMPLETE -> {
                    Logger.warn(LogType.ALERT, "quic listener " + bindAddress + " stopped");
                    cleanup2();
                    yield 0;
                }
                default -> ret;
            };
        }
    }

    private class QuicGatewayConnection extends Connection {
        public QuicGatewayConnection(Options opts) {
            super(opts);
        }

        @Override
        public int callback(QuicConnectionEvent event) {
            int ret = super.callback(event);
            //noinspection SwitchStatementWithTooFewBranches
            return switch (event.getType()) {
                case QUIC_CONNECTION_EVENT_PEER_STREAM_STARTED -> {
                    var data = event.getUnion().getPEER_STREAM_STARTED();
                    var streamHQUIC = data.getStream();
                    var allocator = Allocator.ofUnsafe();
                    var stream_ = new QuicStream(allocator);
                    stream_.setApi(opts.apiTableQ.getApi());
                    stream_.setStream(streamHQUIC);
                    var stream = new QuicGatewayStream(new Stream.Options(this, allocator, stream_));
                    opts.apiTableQ.setCallbackHandler(streamHQUIC, MsQuicUpcall.streamCallback, stream.ref.MEMORY);
                    assert Logger.lowLevelDebug("accepted new quic stream " + stream);
                    yield 0;
                }
                default -> ret;
            };
        }
    }

    private class QuicGatewayStream extends Stream {
        private final Allocator quicBufferAllocator = Allocator.ofConcurrentPooled();
        private final List<QuicBuffer.Array> quicBuffers = new ArrayList<>();

        private io.vproxy.base.connection.Connection backendConn;
        private boolean backendNeedsEof = false;
        private boolean frontendNeedsEof = false;

        // from stream to connection
        private ByteArrayChannel pendingDataFromStream;
        private long pendingDataFromStreamBeginOffset;

        // from connection to stream
        private SimpleRingBufferReaderCommitter committer;

        public QuicGatewayStream(Options opts) {
            super(opts);
        }

        @Override
        public int callback(QuicStreamEvent event) {
            int ret = 0;
            if (event.getType() != QUIC_STREAM_EVENT_SEND_COMPLETE) {
                ret = super.callback(event);
            } else {
                super.logEvent(event); // only log but do not handle
            }
            return switch (event.getType()) {
                case QUIC_STREAM_EVENT_RECEIVE -> {
                    var data = event.getUnion().getRECEIVE();
                    if (data.getBufferCount() <= 0) {
                        if ((data.getFlags() & QUIC_RECEIVE_FLAG_FIN) == QUIC_RECEIVE_FLAG_FIN) {
                            assert Logger.lowLevelDebug("received EOF from " + this);
                            sendEOFToBackend();
                        }
                        yield 0;
                    }
                    pendingDataFromStream = formatDataToChnl(data);
                    if (backendConn == null) {
                        pendingDataFromStreamBeginOffset = 0;
                        data.setTotalBufferLength(0);
                        newConnection();
                        if (backendConn == null) {
                            closeStream();
                            yield 0;
                        }
                        yield QUIC_STATUS_PENDING;
                    } else {
                        int sent = backendConn.getOutBuffer().storeBytesFrom(pendingDataFromStream);
                        // outBuffer may call the writable callback to make `pendingDataFromStream`==null
                        if (pendingDataFromStream != null && pendingDataFromStream.used() > 0) {
                            data.setTotalBufferLength(sent);
                            pendingDataFromStreamBeginOffset = sent;
                            yield QUIC_STATUS_PENDING;
                        }
                        pendingDataFromStream = null;
                        if ((data.getFlags() & QUIC_RECEIVE_FLAG_FIN) == QUIC_RECEIVE_FLAG_FIN) {
                            assert Logger.lowLevelDebug("received EOF from " + this);
                            sendEOFToBackend();
                        }
                        yield 0;
                    }
                }
                case QUIC_STREAM_EVENT_SEND_COMPLETE -> {
                    var data = event.getUnion().getSEND_COMPLETE();
                    var ctx = data.getClientContext();
                    if (ctx != null) {
                        var qbs = new QuicBuffer.Array(ctx.reinterpret(QuicBuffer.LAYOUT.byteSize() * 2));
                        for (var i = 0; i < 2; ++i) {
                            var qb = qbs.get(i);
                            var seg = qb.getBuffer();
                            if (seg == null) {
                                continue;
                            }
                            committer.commit(seg);
                        }
                        quicBuffers.add(qbs);
                        assert Logger.lowLevelDebug("buffer " + qbs.MEMORY + " stored");
                    }
                    if (frontendNeedsEof) {
                        sendEOFToFrontend();
                    }
                    yield 0;
                }
                case QUIC_STREAM_EVENT_PEER_SEND_SHUTDOWN -> {
                    sendEOFToBackend();
                    yield 0;
                }
                default -> ret;
            };
        }

        private ByteArrayChannel formatDataToChnl(QuicStreamEventReceive data) {
            var bufferCount = data.getBufferCount();
            var buffers = new QuicBuffer.Array(
                data.getBuffers().MEMORY.reinterpret(
                    QuicBuffer.LAYOUT.byteSize() * bufferCount));
            ByteArray byteArray = null;
            for (int i = 0; i < bufferCount; ++i) {
                var b = buffers.get(i);
                var arr = ByteArray.from(
                    b.getBuffer().reinterpret(b.getLength())
                );
                if (byteArray == null) byteArray = arr;
                else byteArray = byteArray.concat(arr);
            }
            assert byteArray != null;
            return byteArray.toFullChannel();
        }

        private boolean eofToFrontendIsSent = false;

        private void sendEOFToFrontend() {
            if (eofToFrontendIsSent) {
                return;
            }

            frontendNeedsEof = true;
            if (!committer.isIdle()) {
                return;
            }
            assert Logger.lowLevelDebug("send fin via stream " + this);
            eofToFrontendIsSent = true;
            streamQ.send(null, 0, QUIC_SEND_FLAG_FIN, null);
        }

        private void sendEOFToBackend() {
            backendNeedsEof = true;
            if (pendingDataFromStream != null) {
                return;
            }
            if (backendConn == null) {
                closeStream();
                return;
            }
            assert Logger.lowLevelDebug("call closeWrite on " + backendConn);
            backendConn.closeWrite();
        }

        private void newConnection() {
            var connector = backend.next(opts.connection.getRemoteAddress());
            if (connector == null) {
                Logger.info(LogType.NO_CLIENT_CONN, "the backend " + backend.alias + " refused to provide a remote endpoint");
                return;
            }
            ConnectableConnection backendConn;
            try {
                backendConn = connector.connect(new ConnectionOpts().setTimeout(timeout),
                    RingBuffer.allocateDirect(inBufferSize), RingBuffer.allocateDirect(outBufferSize));
            } catch (IOException e) {
                Logger.error(LogType.CONN_ERROR, "failed to create connection to backend " + connector, e);
                return;
            }
            assert Logger.lowLevelDebug("backend conn starts: " + backendConn);
            var loop = NetEventLoop.current();
            assert loop != null;
            try {
                loop.addConnectableConnection(backendConn, null, new Handler());
            } catch (IOException e) {
                Logger.error(LogType.EVENT_LOOP_ADD_FAIL, "failed to add backend connection " + backendConn + " to event loop " + loop, e);
                backendConn.close();
                return;
            }
            committer = new SimpleRingBufferReaderCommitter((SimpleRingBuffer) backendConn.getInBuffer());
            this.backendConn = backendConn;
        }

        private class Handler implements ConnectableConnectionHandler {
            @Override
            public void connected(ConnectableConnectionHandlerContext ctx) {
                assert Logger.lowLevelDebug("connection " + ctx.connection + " for stream " + QuicGatewayStream.this + " is connected");
                flushData(ctx);
            }

            private void flushData(ConnectionHandlerContext ctx) {
                if (pendingDataFromStream == null) {
                    return;
                }
                ctx.connection.getOutBuffer().storeBytesFrom(pendingDataFromStream);
                // outBuffer may call the writable callback to make `pendingDataFromStream`==null
                if (pendingDataFromStream == null) {
                    return;
                }
                if (pendingDataFromStream.used() != 0) {
                    return;
                }
                var pendingDataFromStream = QuicGatewayStream.this.pendingDataFromStream;
                QuicGatewayStream.this.pendingDataFromStream = null;
                streamQ.receiveComplete(pendingDataFromStream.getReadOff() - pendingDataFromStreamBeginOffset);
                if (backendNeedsEof) {
                    sendEOFToBackend();
                }
            }

            @Override
            public void readable(ConnectionHandlerContext ctx) {
                var segs = committer.read();
                if (segs.length == 0) {
                    return;
                }
                if (segs.length > 2) {
                    Logger.shouldNotHappen("ReaderCommitter should not return array more than 2 elements, but got "
                        + Arrays.toString(segs) + ", committer=" + committer);
                }

                assert Logger.lowLevelDebug("will proxy " + Arrays.toString(segs) + " from " + ctx.connection + " to stream " + QuicGatewayStream.this);
                QuicBuffer.Array qbs;
                if (quicBuffers.isEmpty()) {
                    qbs = new QuicBuffer.Array(quicBufferAllocator, 2);
                    assert Logger.lowLevelDebug("no quic buffer, need to allocate one: " + qbs.MEMORY);
                } else {
                    qbs = quicBuffers.removeLast();
                    assert Logger.lowLevelDebug("reusing quic buffer: " + qbs.MEMORY);
                }
                for (int i = 0; i < segs.length; ++i) {
                    var seg = segs[i];
                    qbs.get(i).setLength((int) seg.byteSize());
                    qbs.get(i).setBuffer(seg);
                }
                for (int i = segs.length; i < 2; ++i) {
                    qbs.get(i).setLength(0);
                    qbs.get(i).setBuffer(MemorySegment.NULL);
                }
                int err = streamQ.send(qbs.get(0), segs.length, QUIC_SEND_FLAG_NONE, qbs.MEMORY);
                if (err != 0) {
                    Logger.error(LogType.SYS_ERROR, "failed sending data through stream " + QuicGatewayStream.this + ": " + err);
                    closeStream();
                }
            }

            @Override
            public void writable(ConnectionHandlerContext ctx) {
                flushData(ctx);
            }

            @Override
            public void exception(ConnectionHandlerContext ctx, IOException err) { // do nothing
            }

            @Override
            public void remoteClosed(ConnectionHandlerContext ctx) {
                sendEOFToFrontend();
            }

            @Override
            public void closed(ConnectionHandlerContext ctx) {
                closeStream();
            }

            @Override
            public void removed(ConnectionHandlerContext ctx) { // do nothing
            }
        }

        @Override
        protected void close0() {
            quicBufferAllocator.close();
            if (backendConn != null) {
                backendConn.close();
            }
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

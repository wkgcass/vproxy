package io.vproxy.base.selector.wrap.quic;

import io.vproxy.base.selector.wrap.AbstractBaseVirtualServerSocketFD;
import io.vproxy.base.selector.wrap.VirtualFD;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.msquic.*;
import io.vproxy.msquic.callback.ConnectionCallback;
import io.vproxy.msquic.callback.ConnectionCallbackList;
import io.vproxy.msquic.wrap.Configuration;
import io.vproxy.msquic.wrap.Connection;
import io.vproxy.msquic.wrap.Listener;
import io.vproxy.msquic.wrap.Registration;
import io.vproxy.pni.Allocator;
import io.vproxy.pni.PooledAllocator;
import io.vproxy.vfd.FD;
import io.vproxy.vfd.IPPort;
import io.vproxy.vfd.ServerSocketFD;

import java.io.IOException;
import java.lang.foreign.MemorySegment;

public class QuicServerSocketFD extends AbstractBaseVirtualServerSocketFD<QuicSocketFD> implements ServerSocketFD, VirtualFD {
    private final boolean withLog;
    private boolean ready = false;
    protected final Connection conn;

    public static QuicServerSocketFD newConnection(Registration reg, Configuration conf, IPPort target) throws IOException {
        return newConnection(false, reg, conf, target);
    }

    public static QuicServerSocketFD newConnection(boolean withLog, Registration reg, Configuration conf, IPPort target) throws IOException {
        return new QuicServerSocketFD(withLog, reg, conf, target);
    }

    public static QuicServerSocketFD wrapAcceptedConnection(Listener listener, Configuration conf, MemorySegment connHQUIC) throws IOException {
        return wrapAcceptedConnection(false, listener, conf, connHQUIC);
    }

    public static QuicServerSocketFD wrapAcceptedConnection(boolean withLog, Listener listener, Configuration conf, MemorySegment connHQUIC) throws IOException {
        return new QuicServerSocketFD(withLog, listener, conf, connHQUIC);
    }

    protected QuicServerSocketFD(boolean withLog, Registration reg, Configuration conf, IPPort target) throws IOException {
        this.withLog = withLog;
        var allocator = Allocator.ofUnsafe();
        conn = new Connection(new Connection.Options(reg, allocator,
            ConnectionCallbackList.withLogIf(withLog,
                new ConnectionCallbackList().add(new ConnectionHandler())
            ),
            ref ->
                reg.opts.registrationQ.openConnection(MsQuicUpcall.connectionCallback, ref.MEMORY, null, allocator)));
        if (conn.connectionQ == null) {
            conn.close();
            throw new IOException("ConnectionOpen failed");
        }
        int err = conn.start(conf, target);
        if (err != 0) {
            conn.close();
            throw new IOException("ConnectionStart failed");
        }
    }

    protected QuicServerSocketFD(boolean withLog, Listener listener, Configuration conf, MemorySegment connHQUIC) throws IOException {
        this.withLog = withLog;
        var connectionAllocator = PooledAllocator.ofUnsafePooled();
        var conn_ = new QuicConnection(connectionAllocator);
        {
            conn_.setApi(listener.opts.apiTableQ.getApi());
            conn_.setHandle(connHQUIC);
        }
        conn = new Connection(new Connection.Options(listener, connectionAllocator,
            ConnectionCallbackList.withLogIf(withLog,
                new ConnectionCallbackList().add(new ConnectionHandler())
            ), conn_));
        var err = conn_.setConfiguration(conf.opts.configurationQ);
        if (err != 0) {
            var errMsg = "set configuration to connection failed: " + err;
            Logger.error(LogType.SYS_ERROR, errMsg);
            conn.close();
            throw new IOException(errMsg);
        }
        conn_.setCallbackHandler(MsQuicUpcall.connectionCallback, conn.ref.MEMORY);
        ready = true;
    }

    public boolean isReady() {
        return ready;
    }

    @Override
    protected void doClose() {
        conn.close();
    }

    @Override
    protected String formatToString() {
        return "QuicServerSocketFD{conn=" + conn + "}";
    }

    @Override
    public boolean contains(FD fd) {
        return fd == this;
    }

    public Connection getConnection() {
        return conn;
    }

    private class ConnectionHandler implements ConnectionCallback {
        @Override
        public int connected(Connection conn, QuicConnectionEventConnected data) {
            ready = true;
            return 0;
        }

        @Override
        public int peerStreamStarted(Connection conn, QuicConnectionEventPeerStreamStarted data) {
            var streamHQUIC = data.getStream();

            var quic = QuicSocketFD.wrapAcceptedStream(withLog, conn, streamHQUIC);
            newAcceptableFD(quic);
            return 0;
        }

        @Override
        public int shutdownComplete(Connection conn, QuicConnectionEventConnectionShutdownComplete data) {
            if (isOpen()) {
                raiseErrorOneTime(new IOException("shutdown complete"));
            }
            return 0;
        }
    }
}

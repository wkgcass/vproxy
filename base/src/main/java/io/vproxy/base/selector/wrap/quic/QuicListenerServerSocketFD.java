package io.vproxy.base.selector.wrap.quic;

import io.vproxy.base.selector.wrap.AbstractBaseVirtualServerSocketFD;
import io.vproxy.base.selector.wrap.VirtualFD;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.msquic.MsQuicUpcall;
import io.vproxy.msquic.QuicConnection;
import io.vproxy.msquic.QuicConnectionEventPeerStreamStarted;
import io.vproxy.msquic.QuicListenerEventNewConnection;
import io.vproxy.msquic.callback.ConnectionCallback;
import io.vproxy.msquic.callback.ConnectionCallbackList;
import io.vproxy.msquic.callback.ListenerCallback;
import io.vproxy.msquic.callback.ListenerCallbackList;
import io.vproxy.msquic.wrap.Connection;
import io.vproxy.msquic.wrap.Listener;
import io.vproxy.pni.PooledAllocator;
import io.vproxy.vfd.FD;
import io.vproxy.vfd.IPPort;
import io.vproxy.vfd.ServerSocketFD;

import java.io.IOException;

public class QuicListenerServerSocketFD extends AbstractBaseVirtualServerSocketFD<QuicSocketFD> implements ServerSocketFD, VirtualFD {
    private final QuicFDs fds;
    private Listener lsn;

    public QuicListenerServerSocketFD(QuicFDs fds) {
        this.fds = fds;
    }

    private void listen(IPPort bindAddress) throws IOException {
        assert Logger.lowLevelDebug("before init msquic listener");

        var listenerAllocator = PooledAllocator.ofUnsafePooled();
        lsn = new Listener(new Listener.Options(fds.reg, listenerAllocator,
            ListenerCallbackList.withLogIf(fds.isWithLog(), new QuicGatewayListenerCallback()), ref ->
            fds.reg.opts.registrationQ.openListener(MsQuicUpcall.listenerCallback, ref.MEMORY, null, listenerAllocator)));
        if (lsn.listenerQ == null) {
            lsn.close();
            throw new IOException("failed creating listener");
        }
        var err = lsn.start(bindAddress, fds.alpn);
        if (err != 0) {
            lsn.close();
            throw new IOException("ListenerStart failed");
        }
    }

    private class QuicGatewayListenerCallback implements ListenerCallback {
        @Override
        public int newConnection(Listener listener, QuicListenerEventNewConnection data) {
            var connHQUIC = data.getConnection();

            var connectionAllocator = PooledAllocator.ofUnsafePooled();
            var conn_ = new QuicConnection(connectionAllocator);
            {
                conn_.setApi(listener.opts.apiTableQ.getApi());
                conn_.setHandle(connHQUIC);
            }
            var conn = new Connection(new Connection.Options(listener, connectionAllocator,
                ConnectionCallbackList.withLogIf(fds.isWithLog(), new ConnectionHandler()), conn_));
            var err = conn_.setConfiguration(fds.conf.opts.configurationQ);
            if (err != 0) {
                var errMsg = STR."set configuration to connection failed: \{err}";
                Logger.error(LogType.SYS_ERROR, errMsg);
                conn.close();
                raiseErrorOneTime(new IOException(errMsg));
                return 0;
            }
            conn_.setCallbackHandler(MsQuicUpcall.connectionCallback, conn.ref.MEMORY);

            assert Logger.lowLevelDebug(STR."accepted new quic connection \{conn}");

            return 0;
        }
    }

    private class ConnectionHandler implements ConnectionCallback {
        @Override
        public int peerStreamStarted(Connection conn, QuicConnectionEventPeerStreamStarted data) {
            var streamHQUIC = data.getStream();
            var quic = QuicSocketFD.wrapAcceptedStream(fds.isWithLog(), conn, streamHQUIC);
            var loop = getLoop();
            if (loop == null) {
                newAcceptableFD(quic);
            } else {
                loop.runOnLoop(() -> newAcceptableFD(quic));
            }
            return 0;
        }
    }

    @Override
    public void bind(IPPort l4addr) throws IOException {
        super.bind(l4addr);
        listen(l4addr);
    }

    @Override
    protected void doClose() {
        lsn.close();
    }

    @Override
    protected String formatToString() {
        return STR."QuicListenerServerSocketFD{lsn=\{lsn}}";
    }

    @Override
    public boolean contains(FD fd) {
        return fd == this;
    }
}

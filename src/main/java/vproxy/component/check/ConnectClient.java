package vproxy.component.check;

import vproxy.connection.*;
import vproxy.selector.TimerEvent;
import vproxy.util.Callback;
import vproxy.util.Logger;
import vproxy.util.RingBuffer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.InterruptedByTimeoutException;

// connect to target address then close the connection
// it's useful when running health check
public class ConnectClient {
    class ConnectClientConnectionHandler implements ClientConnectionHandler {
        private final Callback<Void, IOException> callback;
        private final TimerEvent connectionTimeoutEvent;
        private boolean done = false;
        private TimerEvent delayTimeoutEvent;

        ConnectClientConnectionHandler(Callback<Void, IOException> callback, TimerEvent connectionTimeoutEvent) {
            this.callback = callback;
            this.connectionTimeoutEvent = connectionTimeoutEvent;
        }

        @SuppressWarnings("unchecked")
        @Override
        public void connected(ClientConnectionHandlerContext ctx) {
            cancelTimers(); // cancel timer if possible
            if (checkProtocol == CheckProtocol.tcp) {
                // for non-delay tcp, directly close the connection and return success
                closeAndCallSucc(ctx);
            } else {
                assert checkProtocol == CheckProtocol.tcpDelay;
                // for tcp-delay, wait for a while then close connection
                delayTimeoutEvent = eventLoop.getSelectorEventLoop().delay(
                    50, // fix the delay
                    () -> {
                        // the connection is ok after the timeout
                        // so callback
                        closeAndCallSucc(ctx);
                    });
            }
        }

        @Override
        public void readable(ConnectionHandlerContext ctx) {
            // if readable, the remote endpoint is absolutely alive
            // we close the connection and call callback
            cancelTimers();
            closeAndCallSucc(ctx);
        }

        @Override
        public void writable(ConnectionHandlerContext ctx) {
            // will never fire
        }

        @SuppressWarnings("unchecked")
        @Override
        public void exception(ConnectionHandlerContext ctx, IOException err) {
            cancelTimers(); // cancel timer if possible
            ctx.connection.close(); // close the connection

            assert Logger.lowLevelDebug("exception when doing health check, conn = " + ctx.connection + ", err = " + err);

            if (!callback.isCalled() /*already called by timer*/ && !stopped) callback.failed(err);
        }

        @Override
        public void closed(ConnectionHandlerContext ctx) {
            // check whether is done
            if (done)
                return;
            // "not done and closed" means the remote closed the connection
            // which means: remote is listening, but something went wrong
            // maybe an lb with no healthy backends
            // so we consider it an unhealthy check
            cancelTimers();
            if (!callback.isCalled() /*already called by timer*/ && !stopped)
                callback.failed(new IOException("remote closed"));
        }

        @Override
        public void removed(ConnectionHandlerContext ctx) {
            ctx.connection.close();
        }

        private void cancelTimers() {
            connectionTimeoutEvent.cancel(); // cancel the connection timeout event
            if (delayTimeoutEvent != null) { // cancel the delay event if exists
                delayTimeoutEvent.cancel();
            }
        }

        private void closeAndCallSucc(ConnectionHandlerContext ctx) {
            done = true;
            ctx.connection.close();
            if (!callback.isCalled() /*already called by timer*/ && !stopped) callback.succeeded(null);
        }
    }

    public final NetEventLoop eventLoop;
    public final InetSocketAddress remote;
    public final CheckProtocol checkProtocol;
    public final int timeout;
    private boolean stopped = false;

    public ConnectClient(NetEventLoop eventLoop,
                         InetSocketAddress remote,
                         CheckProtocol checkProtocol,
                         int timeout) {
        this.eventLoop = eventLoop;
        this.remote = remote;
        this.checkProtocol = checkProtocol;
        this.timeout = timeout;
    }

    public void handle(Callback<Void, IOException> cb) {
        // connect to remote
        ClientConnection conn;
        try {
            conn = ClientConnection.create(remote,
                // we do not use the timeout in connection opts
                // because we need to divide the timeouts into several steps
                ConnectionOpts.getDefault(),
                // set input buffer to 1 to be able to read things
                // output buffer is not useful at all here
                RingBuffer.allocate(1), RingBuffer.EMPTY_BUFFER);
        } catch (IOException e) {
            if (!stopped) cb.failed(e);
            return;
        }
        // create a timer handling the connecting timeout
        TimerEvent timer = eventLoop.getSelectorEventLoop().delay(timeout, () -> {
            assert Logger.lowLevelDebug("timeout when doing health check " + conn);
            conn.close();
            if (!cb.isCalled() /*called by connection*/ && !stopped) cb.failed(new InterruptedByTimeoutException());
        });
        try {
            eventLoop.addClientConnection(conn, null, new ConnectClientConnectionHandler(cb, timer));
        } catch (IOException e) {
            if (!stopped) cb.failed(e);
            // exception occurred, so ignore timeout
            timer.cancel();
        }
    }

    public void stop() {
        stopped = true;
    }
}

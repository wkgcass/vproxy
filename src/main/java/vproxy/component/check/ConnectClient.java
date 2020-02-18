package vproxy.component.check;

import vfd.DatagramFD;
import vproxy.app.Config;
import vproxy.connection.*;
import vproxy.dns.DNSClient;
import vproxy.selector.TimerEvent;
import vproxy.util.Callback;
import vproxy.util.Logger;
import vproxy.util.RingBuffer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.InterruptedByTimeoutException;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

// connect to target address then close the connection
// it's useful when running health check
public class ConnectClient {
    class ConnectConnectableConnectionHandler implements ConnectableConnectionHandler {
        private final Callback<Void, IOException> callback;
        private final TimerEvent connectionTimeoutEvent;
        private boolean done = false;
        private TimerEvent delayTimeoutEvent;

        ConnectConnectableConnectionHandler(Callback<Void, IOException> callback, TimerEvent connectionTimeoutEvent) {
            this.callback = callback;
            this.connectionTimeoutEvent = connectionTimeoutEvent;
        }

        @Override
        public void connected(ConnectableConnectionHandlerContext ctx) {
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

        @Override
        public void exception(ConnectionHandlerContext ctx, IOException err) {
            cancelTimers(); // cancel timer if possible
            ctx.connection.close(true); // close the connection with reset

            assert Logger.lowLevelDebug("exception when doing health check, conn = " + ctx.connection + ", err = " + err);

            if (!callback.isCalled() /*already called by timer*/ && !stopped) callback.failed(err);
        }

        @Override
        public void remoteClosed(ConnectionHandlerContext ctx) {
            ctx.connection.close(true);
            closed(ctx);
        }

        @Override
        public void closed(ConnectionHandlerContext ctx) {
            // check whether is done
            if (done)
                return;
            // "not done and closed" means the remote closed the connection
            // which means: remote is listening, but something went wrong
            // maybe an lb with no healthy backend
            // so we consider it an unhealthy check
            cancelTimers();
            if (!callback.isCalled() /*already called by timer*/ && !stopped)
                callback.failed(new IOException("remote closed"));
        }

        @Override
        public void removed(ConnectionHandlerContext ctx) {
            ctx.connection.close(true);
        }

        private void cancelTimers() {
            connectionTimeoutEvent.cancel(); // cancel the connection timeout event
            if (delayTimeoutEvent != null) { // cancel the delay event if exists
                delayTimeoutEvent.cancel();
            }
        }

        private void closeAndCallSucc(ConnectionHandlerContext ctx) {
            done = true;
            ctx.connection.close(true);
            if (!callback.isCalled() /*already called by timer*/ && !stopped) callback.succeeded(null);
        }
    }

    public final NetEventLoop eventLoop;
    public final InetSocketAddress remote;
    public final CheckProtocol checkProtocol;
    public final int timeout;
    private boolean stopped = false;
    private final Consumer<Callback<Void, IOException>> handleFunc;

    private DatagramFD dnsSocket = null;
    private DNSClient dnsClient = null;

    public ConnectClient(NetEventLoop eventLoop,
                         InetSocketAddress remote,
                         CheckProtocol checkProtocol,
                         int timeout) {
        this.eventLoop = eventLoop;
        this.remote = remote;
        this.checkProtocol = checkProtocol;
        this.timeout = timeout;

        switch (this.checkProtocol) {
            case none:
                handleFunc = this::handleNone;
                break;
            case dns:
                handleFunc = this::handleDns;
                break;
            default:
                assert this.checkProtocol == CheckProtocol.tcp || this.checkProtocol == CheckProtocol.tcpDelay;
                handleFunc = this::handleTcp;
                break;
        }
    }

    private void handleNone(Callback<Void, IOException> cb) {
        assert Logger.lowLevelDebug("checkProtocol == none, so directly return success");
        cb.succeeded(null);
    }

    public void handleTcp(Callback<Void, IOException> cb) {
        // connect to remote
        ConnectableConnection conn;
        try {
            conn = ConnectableConnection.create(remote,
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
            conn.close(true);
            if (!cb.isCalled() /*called by connection*/ && !stopped) cb.failed(new InterruptedByTimeoutException());
        });
        try {
            eventLoop.addConnectableConnection(conn, null, new ConnectConnectableConnectionHandler(cb, timer));
        } catch (IOException e) {
            if (!stopped) cb.failed(e);
            // exception occurred, so ignore timeout
            timer.cancel();
        }
    }

    public void handleDns(Callback<Void, IOException> cb) {
        if (dnsSocket == null) {
            dnsSocket = DNSClient.getSocketForDNS();
        }
        if (dnsClient == null) {
            try {
                dnsClient = new DNSClient(eventLoop.getSelectorEventLoop(), dnsSocket, Collections.singletonList(remote), timeout, 1);
            } catch (IOException e) {
                Logger.shouldNotHappen("start dns client failed", e);
                cb.failed(e);
                return;
            }
        }
        dnsClient.resolveIPv4(Config.domainWhichShouldResolve, new Callback<>() {
            @Override
            protected void onSucceeded(List<InetAddress> value) {
                cb.succeeded(null);
            }

            @Override
            protected void onFailed(UnknownHostException err) {
                // we expect the address to be resolved
                cb.failed(err);
            }
        });
    }

    public void handle(Callback<Void, IOException> cb) {
        this.handleFunc.accept(cb);
    }

    public void stop() {
        stopped = true;
        if (dnsClient != null) {
            dnsClient.close();
            dnsClient = null;
        }
        if (dnsSocket != null) {
            try {
                dnsSocket.close();
            } catch (IOException ignore) {
            }
            dnsSocket = null;
        }
    }
}

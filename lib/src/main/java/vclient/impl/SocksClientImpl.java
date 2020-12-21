package vclient.impl;

import vclient.SocksClient;
import vclient.StreamClient;
import vfd.IPPort;
import vlibbase.ConnRef;
import vlibbase.Handler;
import vlibbase.impl.SimpleConnRef;
import vproxybase.connection.*;
import vproxybase.socks.Socks5ClientHandshake;
import vproxybase.util.Callback;
import vproxybase.util.LogType;
import vproxybase.util.Logger;

import java.io.IOException;
import java.util.function.BiFunction;

public class SocksClientImpl extends AbstractClient implements SocksClient {
    private final IPPort remote;
    private final StreamClient streamClient;

    public SocksClientImpl(IPPort remote, SocksClient.Options opts) {
        super(opts);
        this.remote = remote;

        getLoop();
        streamClient = new StreamClientImpl(remote, new StreamClient.Options().fill(opts).setClientContext(getClientContext()));
    }

    @Override
    public void proxy(IPPort target, Handler<ConnRef> cb) {
        proxySocks5((raw, handshakeCb) -> new Socks5ClientHandshake(raw, target, handshakeCb), cb);
    }

    @Override
    public void proxy(String domain, int port, Handler<ConnRef> cb) {
        proxySocks5((raw, handshakeCb) -> new Socks5ClientHandshake(raw, domain, port, handshakeCb), cb);
    }

    private void proxySocks5(BiFunction<Connection, Callback<Void, IOException>, Socks5ClientHandshake> f, Handler<ConnRef> cb) {
        streamClient.connect((err, conn) -> {
            if (err != null) {
                cb.accept(err);
                return;
            }
            var raw = conn.raw();
            var handshake = f.apply(raw, new Callback<>() {
                @Override
                protected void onFailed(IOException err) {
                    raw.close();
                    cb.accept(err);
                }

                @Override
                protected void onSucceeded(Void value) {
                    getLoop().removeConnection(raw);
                    cb.accept(new SimpleConnRef(raw));
                }
            });

            conn.detach();
            try {
                getLoop().addConnectableConnection((ConnectableConnection) raw, null,
                    new Socks5ClientConnectableConnectionHandler(handshake, cb));
            } catch (IOException e) {
                Logger.error(LogType.EVENT_LOOP_ADD_FAIL, "adding " + raw + " to event loop failed", e);
                raw.close();
            }
        });
    }

    @Override
    protected String threadname() {
        return "socks-client-" + remote;
    }

    private static class Socks5ClientConnectableConnectionHandler implements ConnectableConnectionHandler {
        private final Socks5ClientHandshake handshake;
        private final Handler<ConnRef> cb;

        private Socks5ClientConnectableConnectionHandler(Socks5ClientHandshake handshake, Handler<ConnRef> cb) {
            this.handshake = handshake;
            this.cb = cb;
        }

        @Override
        public void connected(ConnectableConnectionHandlerContext ctx) {
            handshake.trigger();
        }

        @Override
        public void readable(ConnectionHandlerContext ctx) {
            handshake.trigger();
        }

        @Override
        public void writable(ConnectionHandlerContext ctx) {
            handshake.trigger();
        }

        @Override
        public void exception(ConnectionHandlerContext ctx, IOException err) {
            cb.accept(err);
            ctx.connection.close();
        }

        @Override
        public void remoteClosed(ConnectionHandlerContext ctx) {
            cb.accept(new IOException("remote closed before socks5 handshaking done"));
            ctx.connection.close();
        }

        @Override
        public void closed(ConnectionHandlerContext ctx) {
            cb.accept(new IOException("closed before socks5 handshaking done"));
        }

        @Override
        public void removed(ConnectionHandlerContext ctx) {
            if (handshake.isDone()) {
                return; // handshake is done, no need to do anything
            }
            ctx.connection.close();
            cb.accept(new IOException("removed from event loop"));
        }
    }
}

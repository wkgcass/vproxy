package vproxybase.connection.util;

import vproxybase.connection.ConnectableConnectionHandler;
import vproxybase.connection.ConnectableConnectionHandlerContext;
import vproxybase.connection.ConnectionHandlerContext;
import vproxybase.util.Callback;
import vproxybase.util.LogType;
import vproxybase.util.Logger;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import java.io.IOException;

public class SSLHandshakeDoneConnectableConnectionHandler implements ConnectableConnectionHandler {
    private final SSLEngine engine;
    private final Callback<Void, IOException> done;

    public SSLHandshakeDoneConnectableConnectionHandler(SSLEngine engine, Callback<Void, IOException> done) {
        this.engine = engine;
        this.done = done;
    }

    private boolean handshakeDone(SSLEngine engine) {
        var status = engine.getHandshakeStatus();
        return status == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING || status == SSLEngineResult.HandshakeStatus.FINISHED;
    }

    private void callbackSuccess(ConnectionHandlerContext ctx) {
        ctx.eventLoop.removeConnection(ctx.connection);
        done.succeeded(null);
    }

    private void callbackFail(ConnectionHandlerContext ctx, IOException err) {
        ctx.connection.close();
        done.failed(err);
    }

    @Override
    public void connected(ConnectableConnectionHandlerContext ctx) {
        if (handshakeDone(engine)) {
            callbackSuccess(ctx);
        }
    }

    @Override
    public void readable(ConnectionHandlerContext ctx) {
        if (handshakeDone(engine)) {
            callbackSuccess(ctx);
        }
    }

    @Override
    public void writable(ConnectionHandlerContext ctx) {
        if (handshakeDone(engine)) {
            callbackSuccess(ctx);
        }
    }

    @Override
    public void exception(ConnectionHandlerContext ctx, IOException err) {
        Logger.error(LogType.CONN_ERROR, "handling https relay handshaking failed: " + ctx.connection + ", got exception", err);
        callbackFail(ctx, err);
    }

    @Override
    public void remoteClosed(ConnectionHandlerContext ctx) {
        closed(ctx);
    }

    @Override
    public void closed(ConnectionHandlerContext ctx) {
        Logger.error(LogType.CONN_ERROR, "handling https relay handshaking failed: " + ctx.connection + ", connection closed");
        callbackFail(ctx, new IOException("closed"));
    }

    @Override
    public void removed(ConnectionHandlerContext ctx) {
        if (!handshakeDone(engine)) {
            Logger.error(LogType.CONN_ERROR, "handling https relay handshaking failed: " + ctx.connection + ", removed from loop");
            callbackFail(ctx, new IOException("removed from loop"));
        }
    }
}

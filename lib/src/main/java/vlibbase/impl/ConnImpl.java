package vlibbase.impl;

import vlibbase.Conn;
import vlibbase.ConnectionAware;
import vproxybase.connection.*;
import vproxybase.util.ByteArray;
import vproxybase.util.LogType;
import vproxybase.util.Logger;
import vproxybase.util.nio.ByteArrayChannel;

import java.io.IOException;
import java.util.LinkedList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ConnImpl implements Conn {
    private final NetEventLoop loop;
    private final Connection conn;
    private final BiConsumer<IOException, Conn> connectedCallbackForClient;
    private boolean connected;

    private Consumer<ByteArray> dataHandler;
    private Consumer<IOException> exceptionHandler;
    private Runnable remoteClosedHandler;
    private Runnable closedHandler;
    private Runnable allWrittenHandler;

    private ByteArrayChannel chnl; // current channel for writing
    private final LinkedList<ByteArray> dataToWrite = new LinkedList<>();

    private boolean tobeRemovedFromLoop = false;
    private boolean isTransferring = false;
    private boolean holdingRef = true; // Conn is holding the ConnRef

    public ConnImpl(NetEventLoop loop, Connection conn, BiConsumer<IOException, Conn> connectedCallbackForClient) throws IOException {
        this.loop = loop;
        this.conn = conn;
        this.connectedCallbackForClient = connectedCallbackForClient;

        if (connectedCallbackForClient != null && !(conn instanceof ConnectableConnection)) {
            throw new IllegalArgumentException("connectedCallback is set but connection is not ConnectableConnection");
        }

        try {
            if (connectedCallbackForClient != null) {
                connected = false;
                loop.addConnectableConnection((ConnectableConnection) conn, null, new ConnectableConnHandler());
            } else {
                connected = true;
                loop.addConnection(conn, null, new SimpleTcpConnHandler());
            }
        } catch (IOException e) {
            Logger.error(LogType.EVENT_LOOP_ADD_FAIL, "adding conn " + conn + " into loop failed", e);
            throw e;
        }
    }

    private class SimpleTcpConnHandler implements ConnectionHandler {
        @Override
        public void readable(ConnectionHandlerContext ctx) {
            int len = ctx.connection.getInBuffer().used();
            ByteArrayChannel chnl = ByteArrayChannel.fromEmpty(len);
            ctx.connection.getInBuffer().writeTo(chnl);
            var data = chnl.getArray();

            if (data == null) {
                Logger.error(LogType.IMPROPER_USE, "dataHandler is not set but data arrives");
            } else {
                dataHandler.accept(data);
            }
        }

        @Override
        public void writable(ConnectionHandlerContext ctx) {
            doWrite();
        }

        @Override
        public void exception(ConnectionHandlerContext ctx, IOException err) {
            if (exceptionHandler == null) {
                Logger.warn(LogType.IMPROPER_USE, "exceptionHandler is not set but exception occurred", err);
            } else {
                exceptionHandler.accept(err);
            }
        }

        @Override
        public void remoteClosed(ConnectionHandlerContext ctx) {
            if (remoteClosedHandler == null) {
                close();
            } else {
                remoteClosedHandler.run();
            }
        }

        @Override
        public void closed(ConnectionHandlerContext ctx) {
            if (closedHandler == null) {
                Logger.error(LogType.IMPROPER_USE, "closedHandler is not set but the connection closed");
            } else {
                closedHandler.run();
            }
        }

        @Override
        public void removed(ConnectionHandlerContext ctx) {
            if (!tobeRemovedFromLoop) {
                Logger.error(LogType.IMPROPER_USE, "the connection " + conn + " is removed from loop");
                conn.close();
            }
        }
    }

    private class ConnectableConnHandler extends SimpleTcpConnHandler implements ConnectableConnectionHandler {
        @Override
        public void connected(ConnectableConnectionHandlerContext ctx) {
            connected = true;
            connectedCallbackForClient.accept(null, ConnImpl.this);
        }

        @Override
        public void exception(ConnectionHandlerContext ctx, IOException err) {
            if (connected) {
                super.exception(ctx, err);
            } else {
                connectedCallbackForClient.accept(err, null);
            }
        }
    }

    @Override
    public Conn data(Consumer<ByteArray> handler) {
        if (dataHandler != null) {
            throw new IllegalArgumentException("dataHandler is already set");
        }
        dataHandler = handler;
        return this;
    }

    @Override
    public Conn exception(Consumer<IOException> handler) {
        if (exceptionHandler != null) {
            throw new IllegalArgumentException("exceptionHandler is already set");
        }
        exceptionHandler = handler;
        return this;
    }

    @Override
    public Conn remoteClosed(Runnable handler) {
        if (remoteClosedHandler != null) {
            throw new IllegalArgumentException("remoteClosedHandler is already set");
        }
        remoteClosedHandler = handler;
        return this;
    }

    @Override
    public Conn closed(Runnable handler) {
        if (closedHandler != null) {
            throw new IllegalStateException("closedHandler is already set");
        }
        closedHandler = handler;
        return this;
    }

    @Override
    public Conn allWritten(Runnable handler) {
        // allow to set null values
        allWrittenHandler = handler;
        return this;
    }

    @Override
    public void write(ByteArray data) {
        if (isTransferring || !holdingRef) {
            throw new IllegalStateException("the connection " + this + " is transferred");
        }
        dataToWrite.add(data);
        doWrite();
    }

    private void doWrite() {
        if (chnl != null) {
            conn.getOutBuffer().storeBytesFrom(chnl);
            if (chnl != null) {
                if (chnl.used() == 0) { // all written
                    chnl = null;
                }
            }
        }
        if (chnl != null) {
            return; // still has data to be sent
        }
        var data = dataToWrite.pollFirst();
        if (data == null) { // no data to write for now
            allWrittenCallback();
            return;
        }
        chnl = ByteArrayChannel.fromFull(data);
        doWrite();
    }

    private void allWrittenCallback() {
        var allWrittenHandler = this.allWrittenHandler;
        if (allWrittenHandler != null) {
            allWrittenHandler.run();
        }
    }

    @Override
    public void closeWrite() {
        if (isTransferring || !holdingRef) {
            throw new IllegalStateException("the connection " + this + " is transferred");
        }
        conn.closeWrite();
    }

    @Override
    public void close() {
        if (!holdingRef) {
            throw new IllegalStateException("the connection " + this + " is transferred");
        }
        tobeRemovedFromLoop = true;
        if (conn.isClosed()) {
            return;
        }
        conn.close();
        if (closedHandler != null) {
            closedHandler.run();
        }
    }

    public boolean isValidRef() {
        return holdingRef;
    }

    @Override
    public boolean isTransferring() {
        return isTransferring;
    }

    @Override
    public <T> T transferTo(ConnectionAware<T> client) throws IOException {
        if (isTransferring) {
            throw new IOException("this connection " + this + " is transferring");
        }
        if (!holdingRef) {
            throw new IOException("this Conn object " + this + " is not holding ConnRef anymore");
        }
        if (!connected) {
            Logger.shouldNotHappen("the connection is not connected but retrieved by user: " + this);
            throw new IOException("the connection is not connected yet");
        }
        if (conn.isClosed()) {
            throw new IOException("the connection is already closed");
        }
        tobeRemovedFromLoop = true;
        loop.removeConnection(conn);
        isTransferring = true;
        T ret = client.receiveTransferredConnection0(this);
        holdingRef = false;
        isTransferring = false;
        return ret;
    }

    @Override
    public Connection raw() {
        return conn;
    }

    @Override
    public String toString() {
        String s = conn.toString();
        s = s.replace("Connection(", "Conn(");
        return s;
    }
}

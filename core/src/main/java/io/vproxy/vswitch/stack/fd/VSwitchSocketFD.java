package io.vproxy.vswitch.stack.fd;

import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.Logger;
import io.vproxy.vfd.IPPort;
import io.vproxy.vfd.SocketFD;
import io.vproxy.vpacket.conntrack.tcp.TcpEntry;
import io.vproxy.vpacket.conntrack.tcp.TcpState;

import java.io.IOException;
import java.nio.ByteBuffer;

public class VSwitchSocketFD extends VSwitchFD implements SocketFD {
    private TcpEntry entry;

    private boolean connected = false;

    private boolean isReadable = false;
    private boolean isWritable = false;

    private boolean fin = false;

    protected VSwitchSocketFD(VSwitchFDContext ctx, TcpEntry entry) {
        super(ctx);
        this.entry = entry;
        this.connected = true;

        entry.setConnectionHandler(new ConnectionHandler());

        if (entry.receivingQueue.hasMoreDataToRead()) {
            isReadable = true;
        }
        isWritable = true;
    }

    public VSwitchSocketFD(VSwitchFDContext ctx) {
        super(ctx);
    }

    private void setReadable() {
        isReadable = true;
        ctx.selector.registerVirtualReadable(this);
    }

    private void cancelReadable() {
        isReadable = false;
        ctx.selector.removeVirtualReadable(this);
    }

    private void setWritable() {
        isWritable = true;
        ctx.selector.registerVirtualWritable(this);
    }

    private void cancelWritable() {
        isWritable = false;
        ctx.selector.removeVirtualWritable(this);
    }

    private void checkEntry() throws IOException {
        if (entry == null) {
            throw new IOException("bind() not called");
        }
    }

    private void checkConnected() throws IOException {
        if (entry == null) {
            throw new IOException("not connected");
        }
    }

    private void checkFin() throws IOException {
        if (fin) {
            throw new IOException("FIN already sent");
        }
    }

    @Override
    public void connect(IPPort l4addr) throws IOException {
        var local = ctx.network.findFreeTcpIPPort(l4addr);
        if (local == null) {
            throw new IOException("unable to find free ip-port to bind");
        }
        entry = ctx.conntrack.createTcp(null, l4addr, local, 0);
        entry.setConnectionHandler(new ConnectionHandler());
        ctx.tcpStack.tcpStartRetransmission(ctx.network, entry);
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void shutdownOutput() throws IOException {
        checkNotClosed();
        checkEntry();
        checkConnected();
        checkFin();

        fin = true;
        if (entry.getState() == TcpState.ESTABLISHED) {
            entry.setState(TcpState.FIN_WAIT_1);
        } else if (entry.getState() == TcpState.CLOSE_WAIT) {
            entry.setState(TcpState.CLOSING);
        } else {
            Logger.shouldNotHappen("should not reach here: " + entry.getState());
        }
        ctx.tcpStack.tcpStartRetransmission(ctx.network, entry);
    }

    @Override
    public boolean finishConnect() throws IOException {
        checkNotClosed();
        checkEntry();
        if (entry.getState() == TcpState.ESTABLISHED) {
            connected = true;
            return true;
        } else {
            return false;
        }
    }

    @Override
    public IPPort getLocalAddress() throws IOException {
        checkEntry();
        return entry.local;
    }

    @Override
    public IPPort getRemoteAddress() throws IOException {
        checkEntry();
        return entry.remote;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        checkNotClosed();
        checkEntry();
        checkConnected();

        int len = dst.limit() - dst.position();
        if (len == 0) {
            return 0;
        }
        ByteArray bytes = entry.receivingQueue.apiRead(len);
        if (bytes == null) {
            // maybe the connection is closed
            if (entry.getState().remoteClosed) {
                return -1;
            }

            return 0;
        }
        int read = bytes.length();
        dst.put(bytes.toJavaArray());

        // handle events
        if (entry.receivingQueue.hasMoreDataToRead()) {
            setReadable();
        } else {
            if (entry.getState().remoteClosed) {
                setReadable();
            } else {
                cancelReadable();
            }
        }

        // need to send ack
        ctx.tcpStack.tcpAck(ctx.network, entry);
        // reset window
        entry.receivingQueue.resetWindow();

        return read;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        checkNotClosed();
        checkEntry();
        checkConnected();
        checkFin();

        int len = src.limit() - src.position();
        if (len == 0) {
            return 0;
        }
        int wrote = entry.sendingQueue.apiWrite(src);

        // start retransmission
        if (wrote > 0) {
            ctx.tcpStack.tcpStartRetransmission(ctx.network, entry);
        }

        // handle events
        if (entry.sendingQueue.hasMoreSpace()) {
            setWritable();
        } else {
            cancelWritable();
        }

        return wrote;
    }

    @Override
    public void onRegister() {
        if (isReadable) {
            setReadable();
        }
        if (isWritable) {
            setWritable();
        }
    }

    @Override
    public void onRemove() {
        // ignore
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;

        cancelReadable();
        cancelWritable();
        if (entry == null) {
            return;
        }

        if (entry.sendingQueue.hasMoreData()) {
            // wait until all data sent
            assert Logger.lowLevelDebug("fd " + this + " is closed, but more data to send, so do not close the connection for now");
            entry.doClose();
            ctx.tcpStack.tcpStartRetransmission(ctx.network, entry);
        } else {
            // send reset
            ctx.tcpStack.resetTcpConnection(ctx.network, entry);
        }
    }

    @Override
    public String toString() {
        return "VSwitchSocketFD(" + entry.remote + "->" + entry.local + ")[" + (closed ? "CLOSED" : "OPEN") + "]";
    }

    private class ConnectionHandler implements io.vproxy.vpacket.conntrack.tcp.ConnectionHandler {
        @Override
        public void readable(TcpEntry entry) {
            if (closed) {
                return;
            }
            setReadable();
        }

        @Override
        public void writable(TcpEntry entry) {
            if (closed) {
                return;
            }
            setWritable();
        }

        @Override
        public void destroy(TcpEntry entry) {
            if (closed) {
                return;
            }
            setReadable();
        }
    }
}

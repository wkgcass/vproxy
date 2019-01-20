package net.cassite.vproxy.connection;

import net.cassite.vproxy.util.Logger;
import net.cassite.vproxy.util.RingBuffer;
import net.cassite.vproxy.util.RingBufferETHandler;
import net.cassite.vproxy.util.Utils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class Connection {
    // the inBuffer might be full, and we need to handle writable event
    class InBufferETHandler implements RingBufferETHandler {
        @Override
        public void readableET() {
            // ignore the event
        }

        @Override
        public void writableET() {
            NetEventLoop eventLoop = _eventLoop;
            if (!closed && eventLoop != null) {
                // the buffer is writable means the channel can read data
                assert Logger.lowLevelDebug("in buffer is writable, add READ for channel " + channel);
                eventLoop.selectorEventLoop.addOps(channel, SelectionKey.OP_READ);
            }
        }
    }

    // the outBuffer might be empty, and we need to handle readable event
    class OutBufferETHandler implements RingBufferETHandler {
        @Override
        public void readableET() {
            NetEventLoop eventLoop = _eventLoop;
            if (!closed && eventLoop != null) {
                // the buffer is readable means the channel can write data
                assert Logger.lowLevelDebug("out buffer is readable, add WRITE for channel " + channel);
                eventLoop.selectorEventLoop.addOps(channel, SelectionKey.OP_WRITE);
            }
        }

        @Override
        public void writableET() {
            // ignore the event
        }
    }

    protected final InetSocketAddress remote;
    protected InetSocketAddress local;
    protected final String _id;
    public final SocketChannel channel;
    /**
     * bytes will be read from channel into this buffer
     */
    public final RingBuffer inBuffer;
    /**
     * bytes in this buffer will be wrote to channel
     */
    public final RingBuffer outBuffer;
    private final InBufferETHandler inBufferETHandler;
    private final OutBufferETHandler outBufferETHandler;
    boolean remoteClosed = false;

    NetEventLoop _eventLoop = null;

    private boolean closed = false;

    public Connection(SocketChannel channel, RingBuffer inBuffer, RingBuffer outBuffer) throws IOException {
        this.channel = channel;
        this.inBuffer = inBuffer;
        this.outBuffer = outBuffer;
        remote = ((InetSocketAddress) channel.getRemoteAddress());
        local = (InetSocketAddress) channel.getLocalAddress();
        _id = genId();

        inBufferETHandler = new InBufferETHandler();
        outBufferETHandler = new OutBufferETHandler();

        this.inBuffer.addHandler(inBufferETHandler);
        this.outBuffer.addHandler(outBufferETHandler);
    }

    protected String genId() {
        return Utils.ipStr(remote.getAddress().getAddress()) + ":" + remote.getPort()
            + "/"
            + (local == null ? "[unbound]" :
            (
                Utils.ipStr(local.getAddress().getAddress()) + ":" + local.getPort()
            )
        );
    }

    public boolean isClosed() {
        return closed;
    }

    // make it synchronized to prevent inside fields inconsistent
    public synchronized void close() {
        if (closed)
            return; // do not close again if already closed

        closed = true;

        inBuffer.removeHandler(inBufferETHandler);
        outBuffer.removeHandler(outBufferETHandler);

        NetEventLoop eventLoop = _eventLoop;
        if (eventLoop != null) {
            eventLoop.removeConnection(this);
        }
        _eventLoop = null;
        try {
            channel.close();
        } catch (IOException e) {
            // we can do nothing about it
        }
    }

    public String id() {
        return _id;
    }

    @Override
    public String toString() {
        return "Connection(" + id() + ")[" + (closed ? "closed" : "open") + "]";
    }
}

package net.cassite.vproxy.connection;

import net.cassite.vproxy.util.Logger;
import net.cassite.vproxy.util.RingBuffer;
import net.cassite.vproxy.util.RingBufferETHandler;

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
            if (!closed && eventLoop != null) {
                // the buffer is writable means the channel can read data
                Logger.lowLevelDebug("in buffer is writable, add READ for channel");
                eventLoop.selectorEventLoop.addOps(channel, SelectionKey.OP_READ);
            }
        }
    }

    // the outBuffer might be empty, and we need to handle readable event
    class OutBufferETHandler implements RingBufferETHandler {
        @Override
        public void readableET() {
            if (!closed && eventLoop != null) {
                // the buffer is readable means the channel can write data
                Logger.lowLevelDebug("out buffer is readable, add WRITE for channel");
                eventLoop.selectorEventLoop.addOps(channel, SelectionKey.OP_WRITE);
            }
        }

        @Override
        public void writableET() {
            // ignore the event
        }
    }

    public final String host;
    public final int port;
    final SocketChannel channel;
    public final RingBuffer inBuffer;
    public final RingBuffer outBuffer;
    private final InBufferETHandler inBufferETHandler;
    private final OutBufferETHandler outBufferETHandler;
    boolean remoteClosed = false;

    NetEventLoop eventLoop = null;

    private boolean closed = false;

    public Connection(SocketChannel channel, RingBuffer inBuffer, RingBuffer outBuffer) throws IOException {
        this.channel = channel;
        this.inBuffer = inBuffer;
        this.outBuffer = outBuffer;
        InetSocketAddress addr = ((InetSocketAddress) channel.getRemoteAddress());
        host = addr.getHostString();
        port = addr.getPort();

        inBufferETHandler = new InBufferETHandler();
        outBufferETHandler = new OutBufferETHandler();

        this.inBuffer.addHandler(inBufferETHandler);
        this.outBuffer.addHandler(outBufferETHandler);
    }

    public boolean isClosed() {
        return closed;
    }

    public void close() {
        closed = true;

        inBuffer.removeHandler(inBufferETHandler);
        outBuffer.removeHandler(outBufferETHandler);

        if (eventLoop != null) {
            eventLoop.selectorEventLoop.remove(channel);
        }
        eventLoop = null;
        try {
            channel.close();
        } catch (IOException e) {
            // we can do nothing about it
        }
    }

    @Override
    public String toString() {
        return "Connection(" + host + ":" + port + ")[" + (closed ? "closed" : "open") + "]";
    }
}

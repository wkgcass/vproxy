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
                eventLoop.getSelectorEventLoop().addOps(channel, SelectionKey.OP_READ);
                // we do not directly read here
                // the reading process requires a corresponding handler
                // the handler may not be a part of this connection lib
                // so we leave it to NetEventLoop to handle the reading

                // for output, there are no handlers attached
                // so it can just write as soon as possible
            }
        }
    }

    // the outBuffer might be empty, and we need to handle readable event
    // we implement a `Quick Write` mechanism
    // when outBuffer is readable,
    // data is directly sent to channel
    // reduce the chance of setting OP_WRITE
    // and may gain some performance
    class OutBufferETHandler implements RingBufferETHandler {
        @Override
        public void readableET() {
            NetEventLoop eventLoop = _eventLoop;
            if (!closed && eventLoop != null) {
                // the buffer is readable means the channel can write data
                assert Logger.lowLevelDebug("out buffer is readable, do WRITE for channel " + channel);
                // let's directly write the data if possible
                // we do not need lock here,
                // all operations about the
                // buffer should be handled in
                // the same thread

                if (outBuffer.free() != 0) {
                    // try to flush buffer in user code
                    // (we assume user code preserves a buffer to write)
                    // (since ringBuffer will not extend)
                    // (which is unnecessary for an lb)
                    _cctx.handler.writable(_cctx);
                }

                boolean addWriteOnLoop = true;
                try {
                    outBuffer.writeTo(channel);
                    if (outBuffer.used() == 0) {
                        // have nothing to write now

                        // at this time, we let user write again
                        // in case there are still some bytes in
                        // user buffer
                        _cctx.handler.writable(_cctx);

                        if (outBuffer.used() == 0) {
                            // outBuffer still empty
                            // do not add OP_WRITE
                            addWriteOnLoop = false;
                        }
                        // we do not write again if got any bytes
                        // let the NetEventLoop handle
                    }
                } catch (IOException ignore) {
                    // we ignore the exception
                    // it should be handled in NetEventLoop
                }
                if (addWriteOnLoop) {
                    eventLoop.getSelectorEventLoop().addOps(channel, SelectionKey.OP_WRITE);
                }
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

    private NetEventLoop _eventLoop = null;
    private ConnectionHandlerContext _cctx = null;

    private boolean closed = false;

    Connection(SocketChannel channel, RingBuffer inBuffer, RingBuffer outBuffer) throws IOException {
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
        releaseEventLoopRelatedFields();
        try {
            channel.close();
        } catch (IOException e) {
            // we can do nothing about it
        }
    }

    void releaseEventLoopRelatedFields() {
        _eventLoop = null;
        _cctx = null;
    }

    void setEventLoopRelatedFields(NetEventLoop eventLoop, ConnectionHandlerContext cctx) {
        _eventLoop = eventLoop;
        _cctx = cctx;
    }

    NetEventLoop getEventLoop() {
        return _eventLoop;
    }

    public String id() {
        return _id;
    }

    @Override
    public String toString() {
        return "Connection(" + id() + ")[" + (closed ? "closed" : "open") + "]";
    }
}

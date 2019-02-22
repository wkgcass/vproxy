package net.cassite.vproxy.connection;

import net.cassite.vproxy.util.Logger;
import net.cassite.vproxy.util.RingBuffer;
import net.cassite.vproxy.util.RingBufferETHandler;
import net.cassite.vproxy.util.Utils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Connection implements NetFlowRecorder {
    /**
     * bytes will be read from channel into this buffer
     */
    public RingBuffer getInBuffer() {
        return inBuffer;
    }

    /**
     * bytes in this buffer will be wrote to channel
     */
    public RingBuffer getOutBuffer() {
        return outBuffer;
    }

    // the inBuffer might be full, and we need to handle writable event
    class InBufferETHandler implements RingBufferETHandler {
        @Override
        public void readableET() {
            // ignore the event
            assert Logger.lowLevelDebug("readableET triggered (do nothing) " + Connection.this);
        }

        @Override
        public void writableET() {
            assert Logger.lowLevelDebug("writableET triggered " + Connection.this);
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
            assert Logger.lowLevelDebug("readableET triggered " + Connection.this);
            NetEventLoop eventLoop = _eventLoop;
            if (!closed && eventLoop != null) {
                // the buffer is readable means the channel can write data
                assert Logger.lowLevelDebug("out buffer is readable, do WRITE for channel " + channel);
                // let's directly write the data if possible
                // we do not need lock here,
                // all operations about the
                // buffer should be handled in
                // the same thread

                if (getOutBuffer().free() != 0) {
                    // try to flush buffer in user code
                    // (we assume user code preserves a buffer to write)
                    // (since ringBuffer will not extend)
                    // (which is unnecessary for an lb)
                    _cctx.handler.writable(_cctx);
                }

                // for server udp channels, just write, and should not add OP_WRITE event
                if (!looksLikeAConnection) {
                    int write;
                    try {
                        write = getOutBuffer().writeToDatagramChannel((DatagramChannel) channel, remote);
                    } catch (IOException ignore) {
                        // we ignore any error when writing udp
                        return;
                    }

                    incToRemoteBytes(write); // record net flow, it's writing, so is "to remote"

                    return; // end `Quick Write`
                }

                boolean addWriteOnLoop = true;
                try {
                    int write = getOutBuffer().writeTo((WritableByteChannel) channel);
                    assert Logger.lowLevelDebug("wrote " + write + " bytes to " + Connection.this);
                    if (write > 0) {
                        incToRemoteBytes(write); // record net flow, it's writing, so is "to remote"
                        // NOTE: should also record in NetEventLoop writable event
                    }
                    if (getOutBuffer().used() == 0) {
                        // have nothing to write now

                        // at this time, we let user write again
                        // in case there are still some bytes in
                        // user buffer
                        _cctx.handler.writable(_cctx);

                        if (getOutBuffer().used() == 0) {
                            // outBuffer still empty
                            // do not add OP_WRITE
                            assert Logger.lowLevelDebug("the out buffer is still empty, do NOT add op_write. " + channel);
                            addWriteOnLoop = false;
                        }
                        // we do not write again if got any bytes
                        // let the NetEventLoop handle
                    }
                } catch (IOException e) {
                    // we ignore the exception
                    // it should be handled in NetEventLoop
                    assert Logger.lowLevelDebug("got exception in quick write: " + e);
                }
                if (addWriteOnLoop) {
                    assert Logger.lowLevelDebug("add OP_WRITE for channel " + channel);
                    eventLoop.getSelectorEventLoop().addOps(channel, SelectionKey.OP_WRITE);
                }
            }
        }

        @Override
        public void writableET() {
            // ignore the event
            assert Logger.lowLevelDebug("writableET triggered (do nothing) " + Connection.this);
        }
    }

    public final InetSocketAddress remote;
    protected InetSocketAddress local;
    protected final String _id;
    public final SelectableChannel channel;
    public final Protocol protocol;
    private final boolean looksLikeAConnection; // this field determines outBufferETHandler's behavior
    BindServer.UDPConn _udpDummyConn; // should be removed when this connection is closed

    // statistics fields
    // the connection is handled in a single thread, so no need to synchronize
    private long toRemoteBytes = 0; // out bytes
    private long fromRemoteBytes = 0; // in bytes
    // since it seldom (in most cases: never) changes, so let's just use a copy on write list
    private final List<NetFlowRecorder> netFlowRecorders = new CopyOnWriteArrayList<>();
    private final List<ConnCloseHandler> connCloseHandlers = new CopyOnWriteArrayList<>();

    private /*only modified in UNSAFE methods*/ RingBuffer inBuffer;
    private /*only modified in UNSAFE methods*/ RingBuffer outBuffer;
    /*private let ClientConnection have access*/ final InBufferETHandler inBufferETHandler;
    private final OutBufferETHandler outBufferETHandler;
    boolean remoteClosed = false;

    private NetEventLoop _eventLoop = null;
    private ConnectionHandlerContext _cctx = null;

    private boolean closed = false;

    Connection(Protocol protocol,
               SelectableChannel channel,
               InetSocketAddress remote,
               RingBuffer inBuffer, RingBuffer outBuffer, boolean looksLikeAConnection) throws IOException {
        this.protocol = protocol;
        this.looksLikeAConnection = looksLikeAConnection;
        assert (protocol == Protocol.TCP && channel instanceof SocketChannel)
            || (protocol == Protocol.UDP && channel instanceof DatagramChannel);
        assert (protocol == Protocol.UDP) || (((SocketChannel) channel).getRemoteAddress().equals(remote));

        this.channel = channel;
        this.inBuffer = inBuffer;
        this.outBuffer = outBuffer;
        this.remote = remote;
        local = (InetSocketAddress) ((NetworkChannel) channel).getLocalAddress();
        _id = genId();

        inBufferETHandler = new InBufferETHandler();
        outBufferETHandler = new OutBufferETHandler();

        if (looksLikeAConnection) {
            // the fd is the server datagram socket, so will not remove OP_READ
            // so there is no need to add OP_READ back
            this.getInBuffer().addHandler(inBufferETHandler);
        }
        this.getOutBuffer().addHandler(outBufferETHandler);
        // in the outBufferETHandler
        // if buffer did not wrote all content, simply ignore the left part
    }

    // --- START statistics ---
    public long getFromRemoteBytes() {
        return fromRemoteBytes;
    }

    public long getToRemoteBytes() {
        return toRemoteBytes;
    }

    @Override
    public void incFromRemoteBytes(long bytes) {
        fromRemoteBytes += bytes;
        for (NetFlowRecorder nfr : netFlowRecorders) {
            nfr.incFromRemoteBytes(bytes);
        }
    }

    @Override
    public void incToRemoteBytes(long bytes) {
        toRemoteBytes += bytes;
        for (NetFlowRecorder nfr : netFlowRecorders) {
            nfr.incToRemoteBytes(bytes);
        }
    }
    // --- END statistics ---

    // NOTE: this is not thread safe
    public void addNetFlowRecorder(NetFlowRecorder nfr) {
        netFlowRecorders.add(nfr);
    }

    // NOTE: this is not thread safe
    public void addConnCloseHandler(ConnCloseHandler cch) {
        connCloseHandlers.add(cch);
    }

    protected String genId() {
        return (protocol == Protocol.UDP ? "UDP:" : "")
            + Utils.ipStr(remote.getAddress().getAddress()) + ":" + remote.getPort()
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

        // actually there's no need to clear the NetFlowRecorders
        // because the connection should not be traced in gc root after it's closed
        // (if you correctly handled all events)
        // but here we clear it since it doesn't hurt
        netFlowRecorders.clear();

        // clear close handler here
        for (ConnCloseHandler h : connCloseHandlers)
            h.onConnClose(this);
        connCloseHandlers.clear();

        // no need to check protocol
        // removing a non-existing element from a collection is safe
        getInBuffer().removeHandler(inBufferETHandler);
        getOutBuffer().removeHandler(outBufferETHandler);

        NetEventLoop eventLoop = _eventLoop;
        if (eventLoop != null && looksLikeAConnection) {
            eventLoop.removeConnection(this);

            /*udp dummy conn does not add to the loop*/
        }
        releaseEventLoopRelatedFields();
        if (looksLikeAConnection) {
            try {
                channel.close();
            } catch (IOException e) {
                // we can do nothing about it
            }

            // the channel is dummy for udp server and should not close
        }

        if (_udpDummyConn != null) {
            _udpDummyConn.remove();
            _udpDummyConn = null;
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

    public NetEventLoop getEventLoop() {
        return _eventLoop;
    }

    public String id() {
        return _id;
    }

    @Override
    public String toString() {
        return "Connection(" + id() + ")[" + (closed ? "closed" : "open") + "]";
    }

    // UNSAFE

    public void UNSAFE_replaceBuffer(RingBuffer in, RingBuffer out) throws IOException {
        assert Logger.lowLevelDebug("UNSAFE_replaceBuffer()");
        // we should make sure that the buffers are empty
        if (getInBuffer().used() != 0 || getOutBuffer().used() != 0) {
            throw new IOException("cannot replace buffers when they are not empty");
        }
        try {
            in = inBuffer.switchBuffer(in);
            out = outBuffer.switchBuffer(out);
        } catch (RingBuffer.RejectSwitchException e) {
            throw new IOException("cannot replace buffers when they are not empty");
        }

        // remove handler from the buffers
        inBuffer.removeHandler(inBufferETHandler);
        outBuffer.removeHandler(outBufferETHandler);

        // add for new buffers
        in.addHandler(inBufferETHandler);
        out.addHandler(outBufferETHandler);

        // assign buffers
        this.inBuffer = in;
        this.outBuffer = out;
    }
}

package vproxybase.connection;

import vfd.EventSet;
import vfd.IPPort;
import vfd.SocketFD;
import vproxybase.selector.TimerEvent;
import vproxybase.util.Logger;
import vproxybase.util.RingBuffer;
import vproxybase.util.RingBufferETHandler;

import java.io.IOException;
import java.net.StandardSocketOptions;
import java.nio.channels.CancelledKeyException;
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
            assert Logger.lowLevelNetDebug("readableET triggered (do nothing) " + Connection.this);
        }

        @Override
        public void writableET() {
            assert Logger.lowLevelDebug("writableET triggered " + Connection.this);
            NetEventLoop eventLoop = _eventLoop;
            if (!closed && eventLoop != null) {
                // the buffer is writable means the channel can read data
                assert Logger.lowLevelDebug("in buffer is writable, add READ for channel " + channel);
                eventLoop.getSelectorEventLoop().addOps(channel, EventSet.read());
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
                NetEventLoopUtils.resetCloseTimeout(_cctx);

                if (noQuickWrite) {
                    assert Logger.lowLevelDebug("quick write is disabled");
                } else {
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
                        if (closed || _eventLoop == null) {
                            assert Logger.lowLevelDebug("the connection is closed or removed from event-loop");
                            return;
                        }
                    }

                    try {
                        int write = getOutBuffer().writeTo(channel);
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

                            // we do not write again if got any bytes
                            // let the NetEventLoop handle
                        }
                    } catch (IOException e) {
                        // we ignore the exception
                        // it should be handled in NetEventLoop
                        assert Logger.lowLevelDebug("got exception in quick write: " + e);
                    }
                }
                if (getOutBuffer().used() != 0) {
                    assert Logger.lowLevelDebug("add OP_WRITE for channel " + channel);
                    eventLoop.getSelectorEventLoop().addOps(channel, EventSet.write());
                } else {
                    // remove the write op in case another method added the OP_WRITE event
                    assert Logger.lowLevelDebug("remove OP_WRITE for channel " + channel);
                    try {
                        eventLoop.getSelectorEventLoop().rmOps(channel, EventSet.write());
                    } catch (CancelledKeyException ignore) {
                        // if the key is invalid, there's no need to remove OP_WRITE
                    }
                }
            }
        }

        @Override
        public void writableET() {
            // ignore the event
            assert Logger.lowLevelNetDebug("writableET triggered (do nothing) " + Connection.this);
        }
    }

    public final IPPort remote;
    protected IPPort local; // may be modified if not connected (in this case, local will be null)
    protected String _id; // may be modified if local was null
    public final SocketFD channel;

    // fields for closing the connection
    TimerEvent closeTimeout; // the connection should be released after a few minutes if no data at all
    long lastTimestamp;
    public final int timeout;

    // statistics fields
    // the connection is handled in a single thread, so no need to synchronize
    private long toRemoteBytes = 0; // out bytes
    private long fromRemoteBytes = 0; // in bytes
    // since it seldom (in most cases: never) changes, so let's just use a copy on write list
    private final List<NetFlowRecorder> netFlowRecorders = new CopyOnWriteArrayList<>();
    private final List<ConnCloseHandler> connCloseHandlers = new CopyOnWriteArrayList<>();

    private /*only modified in UNSAFE methods*/ RingBuffer inBuffer;
    private /*only modified in UNSAFE methods*/ RingBuffer outBuffer;
    /*private let ConnectableConnection have access*/ final InBufferETHandler inBufferETHandler;
    private final OutBufferETHandler outBufferETHandler;

    private NetEventLoop _eventLoop = null;
    private ConnectionHandlerContext _cctx = null;

    private boolean closed = false;
    boolean remoteClosed = false;
    private boolean writeClosed = false;
    private boolean realWriteClosed = false;

    private boolean noQuickWrite = false;

    Connection(SocketFD channel,
               IPPort remote, IPPort local,
               ConnectionOpts opts,
               RingBuffer inBuffer, RingBuffer outBuffer) {
        // set TCP_NODELAY
        try {
            channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
        } catch (Throwable t) {
            assert Logger.lowLevelDebug("set TCP_NODELAY failed: " + channel + ": " + t);
        }

        this.channel = channel;
        this.timeout = opts.timeout;
        this.inBuffer = inBuffer;
        this.outBuffer = outBuffer;
        this.remote = remote;
        this.local = local;
        _id = genId();

        inBufferETHandler = new InBufferETHandler();
        outBufferETHandler = new OutBufferETHandler();

        this.getInBuffer().addHandler(inBufferETHandler);
        this.getOutBuffer().addHandler(outBufferETHandler);
        // in the outBufferETHandler
        // if buffer did not wrote all content, simply ignore the left part
    }

    public IPPort getLocal() {
        return local;
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
        return remote.getAddress().formatToIPString() + ":" + remote.getPort()
            + "/"
            + (local == null ? "[unbound]" :
            (
                local.getAddress().formatToIPString() + ":" + local.getPort()
            )
        );
    }

    public boolean isClosed() {
        return closed;
    }

    public boolean isWriteClosed() {
        return writeClosed;
    }

    public boolean isRemoteClosed() {
        return remoteClosed;
    }

    public void closeWrite() {
        if (realWriteClosed || closed)
            return; // do not close again if already closed

        writeClosed = true; // set write close flag to true

        // check whether output buffer still got data
        if (outBuffer.used() != 0) {
            // NOTE: no need to check for writeClosed in QuickWrite method
            // the method should be called without concurrency
            // so when it reaches here, the OP_WRITE event must be added
            // the closeWrite will be called again in NetEventLoop
            return;
            // do not do real close if got data to write
            // will be closed when the data flushes
        }

        if (!channel.isConnected()) {
            // the connection may not connected yet
            return;
        }

        realWriteClosed = true; // will do real shutdown, so set real write closed flag to true

        // call jdk to close the write end of the connection
        try {
            channel.shutdownOutput();
        } catch (IOException e) {
            // we can do nothing about it
        }

        // here we do not release buffers
    }

    // make it synchronized to prevent inside fields inconsistent
    public synchronized void close() {
        close(false);
    }

    // make it synchronized to prevent inside fields inconsistent
    public synchronized void close(boolean reset) {
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
        _eventLoop = null;
        if (eventLoop != null) {
            eventLoop.removeConnection(this);
        }
        releaseEventLoopRelatedFields();
        if (reset) {
            try {
                channel.setOption(StandardSocketOptions.SO_LINGER, 0);
            } catch (IOException ignore) {
                // ignore if setting SO_LINGER failed
            }
        }
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
            throw new IOException("cannot replace buffers when they are not empty", e);
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

    public void runNoQuickWrite(Runnable r) {
        noQuickWrite = true;
        r.run();
        noQuickWrite = false;
    }
}

package vproxybase.selector.wrap.arqudp;

import vfd.EventSet;
import vfd.FD;
import vfd.IPPort;
import vfd.SocketFD;
import vmirror.MirrorDataFactory;
import vproxybase.Config;
import vproxybase.selector.Handler;
import vproxybase.selector.HandlerContext;
import vproxybase.selector.PeriodicEvent;
import vproxybase.selector.SelectorEventLoop;
import vproxybase.selector.wrap.VirtualFD;
import vproxybase.selector.wrap.WrappedSelector;
import vproxybase.util.ByteArray;
import vproxybase.util.LogType;
import vproxybase.util.Logger;
import vproxybase.util.Utils;
import vproxybase.util.nio.ByteArrayChannel;

import java.io.IOException;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.util.Deque;
import java.util.LinkedList;
import java.util.function.Consumer;
import java.util.function.Function;

public class ArqUDPSocketFD implements SocketFD, VirtualFD {
    private final boolean initiallyConnected;
    private final SocketFD fd;
    private final SelectorEventLoop loop;
    private final ArqUDPHandler handler;
    private final WrappedSelector selector;
    private final ArqUDPInsideFDHandler fdHandler;

    private final Deque<ByteBuffer> readBufs = new LinkedList<>(); // data for application level
    private final Deque<ByteArrayChannel> writeBufs = new LinkedList<>(); // data to network level
    private boolean notFullySent = false; // the flag indicating that it cannot send data

    private PeriodicEvent periodicEvent;

    private final MirrorDataFactory readingMirrorDataFactory;
    private final MirrorDataFactory writingMirrorDataFactory;

    protected ArqUDPSocketFD(SocketFD fd, SelectorEventLoop loop, Function<Consumer<ByteArrayChannel>, ArqUDPHandler> handlerConstructor) {
        this(false, fd, loop, handlerConstructor);
    }

    protected ArqUDPSocketFD(boolean connected, SocketFD fd, SelectorEventLoop loop, Function<Consumer<ByteArrayChannel>, ArqUDPHandler> handlerConstructor) {
        this.initiallyConnected = connected;
        this.fd = fd;
        this.loop = loop;

        this.selector = loop.selector;
        this.fdHandler = new ArqUDPInsideFDHandler();
        this.handler = handlerConstructor.apply(b -> {
            // check `notFullySent` flag
            // if it's set to true, which means the sending queue is not fully sent
            // and the data writing into the handler may be the retransmission packets
            // causing more and more data queuing in the application level
            // so we need to clear the whole queue if it happens
            if (notFullySent) {
                assert Logger.lowLevelDebug("`notFullySent` flag is set, clear the writeBufs queue");
                notFullySent = false; // unset flag
                writeBufs.clear(); // clear the queue
            }
            writeBufs.add(b);
            assert Logger.lowLevelDebug("writeBufs currently have " + writeBufs.size() + " elements");
            fdHandler.watchInsideFDWritable();
        });
        // the fd is always writable when just constructed because writing queue is empty
        setSelfFDWritable();

        // mirrors
        this.readingMirrorDataFactory = new MirrorDataFactory("arq-udp",
            d -> {
                try {
                    IPPort remote = getRemoteAddress();
                    d.setSrc(remote);
                } catch (IOException e) {
                    d.setSrcRef(fd);
                }
                try {
                    IPPort local = getLocalAddress();
                    d.setDst(local);
                } catch (IOException e) {
                    d.setDstRef(this);
                }
            });
        this.writingMirrorDataFactory = new MirrorDataFactory("arq-udp",
            d -> {
                try {
                    IPPort local = getLocalAddress();
                    d.setSrc(local);
                } catch (IOException e) {
                    d.setSrcRef(this);
                }
                try {
                    IPPort remote = getRemoteAddress();
                    d.setDst(remote);
                } catch (IOException e) {
                    d.setDstRef(fd);
                }
            });
    }

    @Override
    public void connect(IPPort l4addr) throws IOException {
        fd.connect(l4addr);
    }

    @Override
    public boolean isConnected() {
        return initiallyConnected || fd.isConnected();
    }

    @Override
    public void shutdownOutput() throws IOException {
        fd.shutdownOutput();
    }

    @Override
    public IPPort getLocalAddress() throws IOException {
        return fd.getLocalAddress();
    }

    @Override
    public IPPort getRemoteAddress() throws IOException {
        return fd.getRemoteAddress();
    }

    @Override
    public boolean finishConnect() throws IOException {
        return fd.finishConnect();
    }

    private void checkException() throws IOException {
        if (fdHandler.getError() != null) {
            throw fdHandler.getError();
        }
    }

    private void mirrorRead(ByteBuffer dstBuf, int posBefore) {
        if (dstBuf.position() == posBefore) { // nothing read
            return;
        }

        String meta = "r=" + readBufs.size() + ";w=" + writeBufs.size() + ";p=" + handler.getClass().getSimpleName();
        readingMirrorDataFactory.build()
            .setMeta(meta)
            .setDataAfter(dstBuf, posBefore)
            .setTransportLayerProtocol("UDP")
            .mirror();
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        if (readBufs.isEmpty()) {
            if (fdHandler.isInvalid()) {
                return -1;
            }
            checkException();
            return 0;
        }

        assert Utils.debug(() -> {
            assert Logger.lowLevelNetDebug("BEGIN: readBufs inside ArqUDPSocketFD:=================");
            for (ByteBuffer b : readBufs) {
                assert Logger.lowLevelNetDebugPrintBytes(b.array(), b.position(), b.limit());
                assert Logger.lowLevelNetDebug("---");
            }
            assert Logger.lowLevelNetDebug("END: readBufs inside ArqUDPSocketFD:=================");
        });

        int oldPos = dst.position();
        int ret = Utils.writeFromFIFOQueueToBuffer(readBufs, dst);

        if (readingMirrorDataFactory.isEnabled()) {
            mirrorRead(dst, oldPos);
        }

        assert Utils.debug(() -> {
            int newPos = dst.position();
            dst.position(oldPos);
            int n = newPos - oldPos;
            byte[] content = new byte[n];
            dst.get(content);
            assert Logger.lowLevelDebug("read " + n + " bytes from " + this);
            assert Logger.lowLevelNetDebugPrintBytes(content);
        });

        checkException();
        if (readBufs.isEmpty() && !fdHandler.isInvalid()) {
            cancelSelfFDReadable();
        }
        return ret;
    }

    public int writableLen() {
        return handler.writableLen();
    }

    private void mirrorWrite(byte[] data) {
        String meta = "r=" + readBufs.size() + ";w=" + writeBufs.size() + ";p=" + handler.getClass().getSimpleName();
        writingMirrorDataFactory.build()
            .setMeta(meta)
            .setData(data)
            .setTransportLayerProtocol("UDP")
            .mirror();
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        int n = src.limit() - src.position();
        if (n == 0) {
            return 0;
        }
        checkException();
        int writableLen = handler.writableLen();
        if (writableLen <= 0 || writableLen < n) {
            cancelSelfFDWritable();
            if (writableLen <= 0) {
                return 0;
            }
        }
        n = Math.min(writableLen, n);
        byte[] copy = new byte[n];
        src.get(copy);

        if (writingMirrorDataFactory.isEnabled()) {
            mirrorWrite(copy);
        }

        assert Logger.lowLevelDebug("write " + n + " bytes to ArqUDPSocketFD");
        assert Logger.lowLevelNetDebugPrintBytes(copy);

        handler.write(ByteArray.from(copy));
        return n;
    }

    @Override
    public void configureBlocking(boolean b) throws IOException {
        fd.configureBlocking(b);
    }

    @Override
    public <T> void setOption(SocketOption<T> name, T value) throws IOException {
        fd.setOption(name, value);
    }

    @Override
    public FD real() {
        return fd.real();
    }

    @Override
    public boolean isOpen() {
        return fd.isOpen();
    }

    @Override
    public void close() throws IOException {
        fd.close();
    }

    @Override
    public void onRegister() {
        EventSet events = EventSet.read();
        if (!writeBufs.isEmpty()) {
            events = events.combine(EventSet.write());
        }
        assert Logger.lowLevelDebug(this + ".onRegister() with events " + events);
        try {
            loop.add(fd, events, null, fdHandler);
        } catch (IOException e) {
            Logger.shouldNotHappen("onRegister callback failed when adding fd " + fd + " to loop", e);
            throw new RuntimeException(e);
        }
        periodicEvent = loop.period(handler.clockInterval(), () -> {
            try {
                handler.clock(Config.currentTimestamp);
            } catch (IOException e) {
                fdHandler.setError(e);
            }
        });

        // register self fd events
        if (selfFDReadable) {
            setSelfFDReadable();
        }
        if (selfFDWritable) {
            setSelfFDWritable();
        }
    }

    @Override
    public void onRemove() {
        assert Logger.lowLevelDebug(this + ".onRemove()");
        if (periodicEvent != null) {
            periodicEvent.cancel();
        }
        loop.remove(fd);
    }

    private boolean selfFDReadable = false;
    private boolean selfFDWritable = false;

    private void setSelfFDReadable() {
        selfFDReadable = true;
        selector.registerVirtualReadable(this);
    }

    private void setSelfFDWritable() {
        selfFDWritable = true;
        selector.registerVirtualWritable(this);
    }

    private void cancelSelfFDReadable() {
        selfFDReadable = false;
        selector.removeVirtualReadable(this);
    }

    private void cancelSelfFDWritable() {
        selfFDWritable = false;
        selector.removeVirtualWritable(this);
    }

    private class ArqUDPInsideFDHandler implements Handler<SocketFD> {
        private final ByteBuffer tmpBuffer = ByteBuffer.allocate(Config.udpMtu);
        private IOException error = null;
        private boolean invalid = false;

        private void setError(IOException error) {
            this.error = error;
            setSelfFDReadable(); // set readable so user code can retrieve the error
        }

        public IOException getError() {
            return error;
        }

        public boolean isInvalid() {
            return invalid;
        }

        private void watchInsideFDReadable() {
            loop.addOps(fd, EventSet.read());
        }

        private void unwatchInsideFDReadable() {
            loop.rmOps(fd, EventSet.read());
        }

        private void watchInsideFDWritable() {
            loop.addOps(fd, EventSet.write());
        }

        private void unwatchInsideFDWritable() {
            try {
                loop.rmOps(fd, EventSet.write());
            } catch (CancelledKeyException ignore) {
                // if it's cancelled, it's removed, so ignore the exception
            }
        }

        @Override
        public void accept(HandlerContext<SocketFD> ctx) {
            // will never fire
        }

        @Override
        public void connected(HandlerContext<SocketFD> ctx) {
            setSelfFDWritable();

            ByteArrayChannel buf = writeBufs.peek();

            if (buf == null) {
                return;
            }

            writable(ctx);
        }

        @Override
        public void readable(HandlerContext<SocketFD> ctx) {
            while (true) {
                int readBytes;
                try {
                    readBytes = ctx.getChannel().read(tmpBuffer);
                } catch (IOException e) {
                    Logger.error(LogType.CONN_ERROR, "reading data from " + ctx.getChannel() + " failed", e);
                    setError(e);
                    unwatchInsideFDReadable();
                    return;
                }
                if (readBytes < 0) {
                    assert Logger.lowLevelDebug("reading data from " + ctx.getChannel() + " failed with " + readBytes);
                    invalid = true;
                    unwatchInsideFDReadable();
                    return;
                }
                if (readBytes == 0) {
                    assert Logger.lowLevelDebug("read nothing, nothing to handle " + ctx.getChannel());
                    return;
                }
                try {
                    readableOne();
                } finally {
                    // reset the tmpBuffer
                    tmpBuffer.limit(tmpBuffer.capacity()).position(0);
                }
            }
        }

        public void readableOne() {
            // copy into the tmp byteArrayChannel
            tmpBuffer.flip();
            int len = tmpBuffer.limit() - tmpBuffer.position();
            if (len == 0) {
                // nothing to be done
                watchInsideFDReadable();
                return;
            }
            ByteArrayChannel tmp = ByteArrayChannel.fromEmpty(len);
            tmp.write(tmpBuffer);

            // read something, try to handle
            // make a copy for data in tmp to make sure it will not be overwritten
            ByteArray b;
            try {
                b = handler.parse(tmp);
            } catch (IOException e) {
                setError(e);
                Logger.error(LogType.CONN_ERROR, "parse kcp packet failed", e);
                unwatchInsideFDReadable();
                return;
            }
            // maybe ack is feed into the handler.parse method
            // so we check whether we can write data now
            assert Logger.lowLevelDebug("checking writable for " + ArqUDPSocketFD.this
                + ", writableLen = " + handler.writableLen());
            if (handler.writableLen() > 0) {
                setSelfFDWritable();
            }
            if (b == null) {
                // still cannot handle
                // want more data, so:
                watchInsideFDReadable();
                return;
            }
            // record the result buffer
            readBufs.add(ByteBuffer.wrap(b.toJavaArray()));
            setSelfFDReadable();
            watchInsideFDReadable();
        }

        @Override
        public void writable(HandlerContext<SocketFD> ctx) {
            assert Logger.lowLevelDebug("writable for " + ctx.getChannel() + " in " + ArqUDPSocketFD.this);
            while (true) {
                ByteArrayChannel buf = writeBufs.peek();

                if (buf == null) {
                    // nothing to write
                    // so we do not care about inside writable event any more
                    unwatchInsideFDWritable();
                    return;
                }
                if (buf.used() == 0) {
                    writeBufs.poll();
                    continue;
                }

                assert Logger.lowLevelDebug("arq udp socket is writing " + buf.used() + " bytes to " + ctx.getChannel());
                assert Logger.lowLevelNetDebugPrintBytes(buf.getBytes(), buf.getReadOff(), buf.getWriteOff());

                // try to write data
                int wlen = buf.used();
                ByteBuffer foo = ByteBuffer.wrap(buf.readableArray().toJavaArray());
                int wrote;
                try {
                    wrote = ctx.getChannel().write(foo);
                } catch (IOException e) {
                    Logger.error(LogType.CONN_ERROR, "writing data to " + ctx.getChannel() + " failed", e);
                    setError(e);
                    return;
                }
                if (wrote < wlen) {
                    assert Logger.lowLevelDebug("not all data wrote: " + (wlen - wrote));
                    if (wrote != 0) {
                        Logger.shouldNotHappen("writing half udp packet to the fd " + ctx.getChannel());
                    }

                    // so the inside fd is not writable for now
                    // watch writable event for inside fd
                    watchInsideFDWritable();
                    // set flag
                    notFullySent = true;
                    return;
                } else {
                    assert Logger.lowLevelDebug("the buffer is empty now, everything wrote");
                    notFullySent = false;
                    buf.skip(wlen);
                }
            }
        }

        @Override
        public void removed(HandlerContext<SocketFD> ctx) {
            invalid = true;
        }
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "(" + fd + ")";
    }
}

package io.vproxy.base.selector.wrap.quic;

import io.vproxy.base.selector.wrap.AbstractBaseVirtualSocketFD;
import io.vproxy.base.selector.wrap.VirtualFD;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.coll.RingQueue;
import io.vproxy.base.util.nio.ByteArrayChannel;
import io.vproxy.msquic.*;
import io.vproxy.msquic.callback.StreamCallback;
import io.vproxy.msquic.callback.StreamCallbackList;
import io.vproxy.msquic.wrap.Connection;
import io.vproxy.msquic.wrap.Stream;
import io.vproxy.pni.Allocator;
import io.vproxy.pni.PooledAllocator;
import io.vproxy.vfd.FD;
import io.vproxy.vfd.IPPort;
import io.vproxy.vfd.SocketFD;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

import static io.vproxy.msquic.MsQuicConsts.*;

public class QuicSocketFD extends AbstractBaseVirtualSocketFD implements SocketFD, VirtualFD {
    private static final int MAX_PENDING_WRITING = 24768;

    private final Stream stream;
    private final RingQueue<ByteArrayChannel> pendingReading = new RingQueue<>();
    private long lastQuicPendingBytes = 0;
    private int pendingWritingBytes = 0;

    public static QuicSocketFD newStream(Connection conn) throws IOException {
        return newStream(false, conn);
    }

    public static QuicSocketFD newStream(boolean withLog, Connection conn) throws IOException {
        return new QuicSocketFD(withLog, conn);
    }

    public static QuicSocketFD wrapAcceptedStream(Connection conn, MemorySegment streamHQUIC) {
        return wrapAcceptedStream(false, conn, streamHQUIC);
    }

    public static QuicSocketFD wrapAcceptedStream(boolean withLog, Connection conn, MemorySegment streamHQUIC) {
        return new QuicSocketFD(withLog, conn, streamHQUIC);
    }

    protected QuicSocketFD(boolean withLog, Connection conn) throws IOException {
        super(false, conn.getLocalAddress(), conn.getRemoteAddress());

        assert Logger.lowLevelDebug(STR."creating quic stream for conn \{conn}");

        var allocator = PooledAllocator.ofUnsafePooled();
        stream = new Stream(new Stream.Options(conn, allocator,
            StreamCallbackList.withLogIf(withLog, new StreamHandler()), ref ->
            conn.connectionQ.openStream(QUIC_STREAM_OPEN_FLAG_NONE, MsQuicUpcall.streamCallback, ref.MEMORY, null, allocator)));
        if (stream.streamQ == null) {
            throw new IOException(STR."failed to create stream for \{conn}");
        }

        assert Logger.lowLevelDebug(STR."quic stream is created for conn \{conn}");

        int ret = stream.start(QUIC_STREAM_START_FLAG_FAIL_BLOCKED);
        if (ret != 0) {
            throw new IOException(STR."failed to start stream for \{conn}: \{ret}");
        }

        assert Logger.lowLevelDebug(STR."quic stream is started for conn \{conn}");
    }

    protected QuicSocketFD(boolean withLog, Connection conn, MemorySegment streamHQUIC) {
        super(true, conn.getLocalAddress(), conn.getRemoteAddress());

        var allocator = PooledAllocator.ofUnsafePooled();
        var stream_ = new QuicStream(allocator);
        stream_.setApi(conn.opts.apiTableQ.getApi());
        stream_.setHandle(streamHQUIC);
        stream = new Stream(new Stream.Options(conn, allocator,
            StreamCallbackList.withLogIf(withLog, new StreamHandler()), stream_));
        stream_.setCallbackHandler(MsQuicUpcall.streamCallback, stream.ref.MEMORY);

        setWritable();
    }

    @Override
    public void connect(IPPort l4addr) {
        try {
            super.connect(l4addr);
        } catch (IOException e) {
            Logger.shouldNotHappen("calling AbstractBaseVirtualSocketFD.connect failed", e);
        }
    }

    @Override
    protected boolean noDataToRead() {
        return pendingReading.isEmpty();
    }

    @Override
    protected int doRead(ByteBuffer dst) {
        if (pendingReading.isEmpty()) {
            return 0;
        }
        int n = 0;
        while (true) {
            var o = pendingReading.peek();
            if (o == null) {
                break;
            }
            n += o.read(dst);
            if (o.used() != 0) {
                break;
            }
            pendingReading.poll();
        }
        if (pendingReading.isEmpty()) {
            var last = lastQuicPendingBytes;
            lastQuicPendingBytes = 0;
            stream.streamQ.receiveComplete(last);
        }
        return n;
    }

    @Override
    protected boolean noSpaceToWrite() {
        return pendingWritingBytes >= MAX_PENDING_WRITING;
    }

    @Override
    protected int doWrite(ByteBuffer src) throws IOException {
        if (src.limit() == src.position()) {
            return 0;
        }
        var pool = PooledAllocator.ofUnsafePooled();
        var mem = pool.allocate(src.limit() - src.position());
        mem.copyFrom(MemorySegment.ofBuffer(src));
        pendingWritingBytes += (int) mem.byteSize();
        int ret = stream.send(0, new SendContext(pool, (int) mem.byteSize()), mem);
        if (ret != 0) {
            throw new IOException(STR."failed to send data to quic stream: \{ret}");
        }
        src.position(src.limit());
        return (int) mem.byteSize();
    }

    private class SendContext extends io.vproxy.msquic.wrap.SendContext {
        public SendContext(Allocator allocator, int size) {
            super(allocator, (ctx, _) -> {
                pendingWritingBytes -= size;
                ctx.allocator.close();
            });
        }
    }

    @Override
    public void shutdownOutput() throws IOException {
        super.shutdownOutput();
        stream.sendFin();
    }

    @Override
    protected void doClose(boolean reset) {
        stream.close();
    }

    @Override
    protected String formatToString() {
        return STR."QuicSocketFD{stream=\{stream}}";
    }

    @Override
    public boolean contains(FD fd) {
        return fd == this;
    }

    private boolean isValid() {
        if (!isOpen())
            return false;
        return !isEof() || !isShutdownOutput();
    }

    public Stream getStream() {
        return stream;
    }

    private class StreamHandler implements StreamCallback {
        @Override
        public int startComplete(Stream stream, QuicStreamEventStartComplete data) {
            if (data.getStatus() != 0) {
                raiseError(new IOException(STR."start failed: \{data.getStatus()}"));
            } else {
                alertConnected(stream.opts.connection.getLocalAddress());
            }
            return 0;
        }

        @Override
        public int receive(Stream stream, QuicStreamEventReceive data) {
            var totalLen = data.getTotalBufferLength();
            var cnt = data.getBufferCount();

            if (totalLen == 0 || (data.getFlags() & QUIC_RECEIVE_FLAG_FIN) != 0) {
                assert Logger.lowLevelDebug("received FIN from remote");
                setEof();
                return 0;
            }

            var bufMem = data.getBuffers().MEMORY;
            bufMem = bufMem.reinterpret(QuicBuffer.LAYOUT.byteSize() * cnt);
            var bufs = new QuicBuffer.Array(bufMem);

            for (var i = 0; i < cnt; ++i) {
                var buf = bufs.get(i);
                var byteArray = QuicBufferByteArray.of(buf);
                pendingReading.add(ByteArrayChannel.fromFull(byteArray));
            }

            setReadable();

            data.setTotalBufferLength(0);
            lastQuicPendingBytes = totalLen;
            return QUIC_STATUS_PENDING;
        }

        @Override
        public int sendComplete(Stream stream, QuicStreamEventSendComplete data) {
            if (!noSpaceToWrite()) {
                setWritable();
            }
            return 0;
        }

        @Override
        public int peerSendShutdown(Stream stream) {
            setEof();
            return 0;
        }

        @Override
        public int peerSendAborted(Stream stream, QuicStreamEventPeerSendAborted data) {
            setEof();
            return 0;
        }

        @Override
        public int shutdownComplete(Stream stream, QuicStreamEventShutdownComplete data) {
            if (isValid()) {
                raiseError(new IOException("shutdown complete"));
            }
            return 0;
        }
    }
}

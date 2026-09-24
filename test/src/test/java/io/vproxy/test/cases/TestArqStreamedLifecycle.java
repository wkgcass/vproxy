package io.vproxy.test.cases;

import io.vproxy.base.Config;
import io.vproxy.base.GlobalInspection;
import io.vproxy.base.selector.*;
import io.vproxy.base.selector.wrap.VirtualFD;
import io.vproxy.base.selector.wrap.arqudp.*;
import io.vproxy.base.selector.wrap.h2streamed.H2StreamedFDHandler;
import io.vproxy.base.selector.wrap.kcp.KCPHandler;
import io.vproxy.base.selector.wrap.kcp.Kcp;
import io.vproxy.base.selector.wrap.kcp.mock.ByteBuf;
import io.vproxy.base.selector.wrap.streamed.*;
import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.bytearray.CompositeByteArray;
import io.vproxy.base.util.bytearray.SubByteArray;
import io.vproxy.base.util.nio.ByteArrayChannel;
import io.vproxy.base.util.time.TimeElem;
import io.vproxy.vfd.*;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.Assert.*;

public class TestArqStreamedLifecycle {
    @Test
    public void partialFramesDoNotRetainConsumedReceiveData() throws Exception {
        try (Fixture f = new Fixture()) {
            StreamHandler handler = new StreamHandler();
            init(handler, f, idleCallback());
            try {
                handler.syn(1);
                StreamedFD stream = (StreamedFD) map(handler, "fdMap").get(1);
                byte[] payload = new byte[512];
                new Random(123).nextBytes(payload);
                ByteArray frame = dataFrame(1, ByteArray.from(payload));
                f.transport.incoming = frame.sub(0, 1).toJavaArray();
                f.inside.readable(f.context);
                handler.readable(null);
                byte[] chunk = frame.sub(1, frame.length() - 1).concat(frame.sub(0, 1)).toJavaArray();
                int limit = (int) get(handler, "MAX_READ_BATCH_BYTES");
                for (int i = 0; i < 1024; ++i) {
                    // Finish one frame while leaving a byte of the next header cached.
                    f.transport.incoming = chunk;
                    f.inside.readable(f.context);
                    handler.readable(null);
                    ByteBuffer out = ByteBuffer.allocate(payload.length);
                    assertEquals(payload.length, stream.read(out));
                    assertArrayEquals(payload, out.array());
                    ByteArray cached = (ByteArray) get(handler, "cachedReceivedMessage");
                    assertEquals(1, cached.length());
                    assertTrue("consumed backing data retained at frame " + i,
                        backingBytes(cached) - cached.length() <= limit + frame.length());
                }
                stream.close();
            } finally {
                call(handler, "clear", new Class<?>[0]);
            }
        }
    }

    private static long backingBytes(ByteArray array) {
        Set<ByteArray> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<ByteArray> pending = new ArrayDeque<>();
        pending.push(array);
        long bytes = 0;
        while (!pending.isEmpty()) {
            ByteArray current = pending.pop();
            if (!seen.add(current)) continue;
            if (current instanceof CompositeByteArray composite) {
                pending.push(composite.getFirst());
                pending.push(composite.getSecond());
            } else if (current instanceof SubByteArray sub) {
                pending.push(sub.source);
            } else {
                bytes += current.length();
            }
        }
        return bytes;
    }

    @Test
    public void disconnectedUnacceptedStreamsLeaveAcceptQueue() throws Exception {
        try (Fixture f = new Fixture()) {
            StreamHandler handler = new StreamHandler();
            StreamedServerSocketFD server = new StreamedServerSocketFD(null, f.loop, new IPPort(28191), new StreamedServerSocketFD[1]);
            init(handler, f, new StreamConnectionStateCallback() {
                public void onReady(ArqUDPSocketFD fd) { }
                public void onInvalid(ArqUDPSocketFD fd) { }
                public boolean onAccept(StreamedFD fd) {
                    try {
                        call(server, "accepted", new Class<?>[]{StreamedFD.class}, fd);
                        return true;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            });
            try {
                List<StreamedFD> streams = new ArrayList<>();
                for (int id : new int[]{1, 3, 5}) {
                    assertTrue(handler.syn(id));
                    handler.data(id, ByteArray.allocate(4096));
                    streams.add((StreamedFD) map(handler, "fdMap").get(id));
                }
                assertSame(streams.get(0), server.accept());
                handler.removed(null);
                assertTrue(server.isOpen());
                assertSame(streams.get(1), server.accept());
                assertSame(streams.get(2), server.accept());
                assertNull(server.accept());
                assertTrue(queue(server, "acceptQueue").isEmpty());
                assertEquals(StreamedFD.State.dead, streams.get(0).getState());
                assertEquals(StreamedFD.State.dead, streams.get(1).getState());
                assertEquals(StreamedFD.State.dead, streams.get(2).getState());
                for (StreamedFD stream : streams) {
                    stream.close();
                    assertTrue(queue(stream, "readableBuffers").isEmpty());
                }
                assertEquals(0L, get(handler, "totalBufferedBytes"));
            } finally {
                server.close();
                call(handler, "clear", new Class<?>[0]);
            }
        }
    }

    @Test
    public void slowStreamPausesConnectionAndResumesCachedFrames() throws Exception {
        checkReceiveResume(false);
        checkReceiveResume(true);
    }

    private void checkReceiveResume(boolean closeSlowStream) throws Exception {
        try (Fixture f = new Fixture()) {
            StreamHandler handler = new StreamHandler();
            init(handler, f, idleCallback());
            try {
                handler.syn(1);
                handler.syn(3);
                StreamedFD slow = (StreamedFD) map(handler, "fdMap").get(1);
                StreamedFD other = (StreamedFD) map(handler, "fdMap").get(3);
                int high = (int) get(handler, "STREAM_READ_BUFFER_HIGH_WATERMARK");
                ByteArray payload = ByteArray.allocate(high);
                ByteArray frames = dataFrame(1, payload).concat(dataFrame(3, ByteArray.from(7, 8, 9)));
                set(handler, "cachedReceivedMessage", frames);
                handler.readable(null);
                assertEquals(true, get(handler, "transportReadPaused"));
                assertFalse(f.loop.getOps(f.fd).have(Event.READABLE));
                assertEquals(0, other.read(ByteBuffer.allocate(3)));
                handler.readable(null); // stale readiness must not bypass backpressure
                assertEquals(0, other.read(ByteBuffer.allocate(3)));
                handler.writable(null); // Flush SYNACKs so the idle check can send a heartbeat.
                set(handler, "lastReadableTimestamp", Config.currentTimestamp - 6_000);
                call(handler, "keepalive", new Class<?>[0]);
                assertTrue(map(handler, "keepaliveTimeouts").isEmpty());
                if (closeSlowStream) {
                    slow.close();
                } else {
                    assertEquals(high, slow.read(ByteBuffer.allocate(high)));
                }
                assertEquals(true, get(handler, "transportReadResumeScheduled"));
                // The queued resume must handle the cached frame without any
                // additional network data (nor an underlying readable event).
                f.loop.onePoll();
                assertEquals(false, get(handler, "transportReadPaused"));
                assertTrue(f.loop.getOps(f.fd).have(Event.READABLE));
                ByteBuffer out = ByteBuffer.allocate(3);
                assertEquals(3, other.read(out));
                assertArrayEquals(new byte[]{7, 8, 9}, out.array());
                assertNull(get(handler, "cachedReceivedMessage"));
                assertEquals(0L, get(handler, "totalBufferedBytes"));
                slow.close();
                other.close();
            } finally {
                call(handler, "clear", new Class<?>[0]);
            }
        }
    }

    @Test
    public void pausedReadsCancelOutstandingKeepaliveAndResumeTimeoutDetection() throws Exception {
        try (Fixture f = new Fixture()) {
            StreamHandler handler = new StreamHandler();
            init(handler, f, idleCallback());
            try {
                handler.syn(1);
                handler.writable(null);
                startKeepalive(handler, f);
                TimerEvent timer = (TimerEvent) map(handler, "keepaliveTimeouts").get(1L);
                int high = (int) get(handler, "STREAM_READ_BUFFER_HIGH_WATERMARK");
                handler.data(1, ByteArray.allocate(high));
                assertEquals(true, get(timer, "canceled"));
                assertTrue(map(handler, "keepaliveTimeouts").isEmpty());
                assertEquals(false, get(handler, "isFailed"));

                StreamedFD stream = (StreamedFD) map(handler, "fdMap").get(1);
                assertEquals(high, stream.read(ByteBuffer.allocate(high)));
                f.loop.onePoll();
                assertEquals(false, get(handler, "transportReadPaused"));
                call(handler, "keepalive", new Class<?>[0]);
                assertTrue("resume starts a fresh idle period", map(handler, "keepaliveTimeouts").isEmpty());
                handler.ack(1);
                assertEquals(false, get(handler, "isFailed"));

                Runnable unansweredTimeout = startKeepalive(handler, f);
                unansweredTimeout.run();
                assertEquals("a silent peer must still time out after resume", true, get(handler, "isFailed"));
            } finally {
                call(handler, "clear", new Class<?>[0]);
            }
        }
    }

    @Test
    public void receiveActivityAfterKeepalivePreventsTimeout() throws Exception {
        try (Fixture f = new Fixture()) {
            StreamHandler handler = new StreamHandler();
            init(handler, f, idleCallback());
            try {
                handler.syn(1);
                Runnable timeout = startKeepalive(handler, f);
                set(handler, "keepaliveSuccessCount", 1);
                // Ordinary DATA arriving after the PING must suffice; no PING ACK is supplied.
                f.transport.incoming = dataFrame(1, ByteArray.from(7, 8, 9)).toJavaArray();
                f.inside.readable(f.context);
                handler.readable(null);
                timeout.run();
                assertEquals(false, get(handler, "isFailed"));
                assertEquals("active traffic must not consume timeout tolerance", 1, get(handler, "keepaliveSuccessCount"));
                assertTrue(map(handler, "keepaliveTimeouts").isEmpty());
                StreamedFD stream = (StreamedFD) map(handler, "fdMap").get(1);
                ByteBuffer out = ByteBuffer.allocate(3);
                assertEquals(3, stream.read(out));
                assertArrayEquals(new byte[]{7, 8, 9}, out.array());

                // Once receive activity becomes stale, ordinary timeout handling must resume.
                startKeepalive(handler, f).run();
                assertEquals(0, get(handler, "keepaliveSuccessCount"));
                assertEquals(false, get(handler, "isFailed"));
                startKeepalive(handler, f).run();
                assertEquals(true, get(handler, "isFailed"));
            } finally {
                call(handler, "clear", new Class<?>[0]);
            }
        }
    }

    private static Runnable startKeepalive(StreamHandler handler, Fixture f) throws Exception {
        handler.writable(null);
        set(handler, "lastReadableTimestamp", Config.currentTimestamp - 6_000);
        call(handler, "keepalive", new Class<?>[0]);
        assertEquals(1, map(handler, "keepaliveTimeouts").size());
        TimerEvent timer = (TimerEvent) map(handler, "keepaliveTimeouts").values().iterator().next();
        f.loop.onePoll(); // Install the timer without waiting five wall-clock seconds.
        return (Runnable) ((TimeElem<?>) get(timer, "event")).get();
    }

    @Test
    public void aggregateReceiveLimitPausesManySmallStreams() throws Exception {
        try (Fixture f = new Fixture()) {
            StreamHandler handler = new StreamHandler();
            init(handler, f, idleCallback());
            try {
                int chunk = (int) get(handler, "STREAM_READ_BUFFER_HIGH_WATERMARK") / 2;
                int total = (int) get(handler, "CONNECTION_READ_BUFFER_HIGH_WATERMARK");
                for (int i = 0; i < total / chunk; ++i) {
                    handler.syn(i * 2 + 1);
                    handler.data(i * 2 + 1, ByteArray.allocate(chunk));
                }
                assertEquals(true, get(handler, "transportReadPaused"));
                assertTrue(((Set<?>) get(handler, "streamsOverReadHighWatermark")).isEmpty());
                assertEquals((long) total, get(handler, "totalBufferedBytes"));
                call(handler, "clear", new Class<?>[0]);
                assertEquals(0L, get(handler, "totalBufferedBytes"));
            } finally {
                call(handler, "clear", new Class<?>[0]);
            }
        }
    }

    @Test
    public void kcpCloseReleasesAckArrayAndAllSegmentQueues() throws Exception {
        try (Fixture f = new Fixture(TestKCPHandler::new)) {
            Kcp kcp = (Kcp) get(get(f.fd, "handler"), "kcp");
            kcp.send(new ByteBuf(ByteArrayChannel.fromFull(ByteArray.from(1, 2, 3))));
            kcp.update(1);
            kcp.send(new ByteBuf(ByteArrayChannel.fromFull(ByteArray.from(4, 5, 6))));
            kcp.input(new ByteBuf(ByteArrayChannel.fromFull(kcpData(0))));
            kcp.input(new ByteBuf(ByteArrayChannel.fromFull(kcpData(2))));
            for (int i = 0; i < 2048; ++i) {
                call(kcp, "ackPush", new Class<?>[]{long.class, long.class}, 0L, (long) i);
            }
            for (String name : List.of("sndQueue", "sndBuf", "rcvQueue", "rcvBuf")) {
                assertFalse(name, queue(kcp, name).isEmpty());
            }
            assertTrue(((int[]) get(kcp, "acklist")).length > 1024);
            f.fd.close();
            f.fd.close();
            for (String name : List.of("sndQueue", "sndBuf", "rcvQueue", "rcvBuf")) {
                assertTrue(name, queue(kcp, name).isEmpty());
            }
            assertEquals(8, ((int[]) get(kcp, "acklist")).length);
            assertEquals(0, get(kcp, "ackcount"));
        }
    }

    @Test
    public void kcpReceiveWindowBackpressuresAndRecoversWithoutDataLoss() throws Exception {
        try (Fixture sender = new Fixture(TestKCPHandler::new);
             Fixture receiver = new Fixture(TestKCPHandler::new)) {
            byte[] expected = new byte[3 * 1024 * 1024];
            new Random(123).nextBytes(expected);
            ByteBuffer input = ByteBuffer.wrap(expected);
            int limit = (int) get(receiver.fd, "READ_HIGH_WATERMARK");
            long ts = 1;
            for (int i = 0; i < 300; ++i, ts += 10) {
                sender.fd.write(input);
                pumpKcp(sender, receiver, ts);
            }
            assertTrue("sender must stop when receive window fills", input.hasRemaining());
            int queued = (int) get(receiver.fd, "readBufferedBytes");
            assertTrue(queued >= limit);
            assertTrue("bounded application queue plus one KCP message", queued < limit + 1250);
            Kcp receiverKcp = (Kcp) get(get(receiver.fd, "handler"), "kcp");
            assertTrue(queue(receiverKcp, "rcvQueue").size() <= 64);
            assertTrue(queue(receiverKcp, "rcvBuf").size() <= 64);

            // ACKs for reverse-direction traffic must still be handled even
            // though the receiver application's queue is full.
            receiver.fd.write(ByteBuffer.wrap(new byte[]{11, 22, 33}));
            for (int i = 0; i < 20; ++i, ts += 10) {
                pumpKcp(sender, receiver, ts);
            }
            ByteBuffer reverse = ByteBuffer.allocate(3);
            assertEquals(3, sender.fd.read(reverse));
            assertArrayEquals(new byte[]{11, 22, 33}, reverse.array());
            assertEquals(0, receiverKcp.waitSnd());

            ByteBuffer output = ByteBuffer.allocate(expected.length);
            // No new packet is delivered during this drain. Pending protocol
            // receive data must be made readable by the application reads.
            while (receiver.fd.read(output) > 0) { }
            assertTrue(queue(receiverKcp, "rcvQueue").isEmpty());
            assertTrue(queue(receiver.fd, "readBufs").isEmpty());
            for (int i = 0; output.hasRemaining() && i < 2000; ++i, ts += 10) {
                sender.fd.write(input);
                pumpKcp(sender, receiver, ts);
                while (receiver.fd.read(output) > 0) { }
            }
            assertFalse("all data should be sent after resuming reads", input.hasRemaining());
            assertFalse("all data should arrive after window recovery", output.hasRemaining());
            assertArrayEquals(expected, output.array());
        }
    }

    private static void pumpKcp(Fixture a, Fixture b, long timestamp) throws Exception {
        ((ArqUDPHandler) get(a.fd, "handler")).clock(timestamp);
        ((ArqUDPHandler) get(b.fd, "handler")).clock(timestamp);
        deliver(a, b);
        deliver(b, a);
    }

    private static void deliver(Fixture from, Fixture to) {
        from.inside.writable(from.context);
        for (byte[] packet : from.transport.sent) {
            to.transport.incoming = packet;
            to.inside.readable(to.context);
        }
        from.transport.sent.clear();
    }

    private static ByteArray dataFrame(int id, ByteArray payload) {
        return ByteArray.allocate(9).int24(0, payload.length()).int32(5, id).concat(payload);
    }

    private static ByteArray kcpData(int sequence) {
        return ByteArray.from(ByteBuffer.allocate(25).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0).put((byte) Kcp.IKCP_CMD_PUSH).put((byte) 0).putShort((short) 64)
            .putInt(1).putInt(sequence).putInt(0).putInt(1).put((byte) 42).array());
    }

    private static StreamConnectionStateCallback idleCallback() {
        return new StreamConnectionStateCallback() {
            public void onReady(ArqUDPSocketFD fd) { }
            public void onInvalid(ArqUDPSocketFD fd) { }
            public boolean onAccept(StreamedFD fd) { return true; }
        };
    }

    private static class TestKCPHandler extends KCPHandler {
        TestKCPHandler(Consumer<ByteArrayChannel> emitter) { super(emitter, "test", options()); }
        private static KCPOptions options() {
            KCPOptions opts = new KCPOptions();
            opts.sndWnd = 64;
            opts.rcvWnd = 64;
            return opts;
        }
    }

    @Test
    public void blockedWritesDropStalePackets() throws Exception {
        try (Fixture f = new Fixture()) {
            f.transport.blocked = true;
            f.fd.write(ByteBuffer.wrap(new byte[]{1, 2}));
            f.inside.writable(f.context);
            f.fd.write(ByteBuffer.wrap(new byte[]{3, 4}));
            f.inside.writable(f.context);
            assertEquals(2, f.transport.blockedWrites);
            assertEquals(1, queue(f.fd, "writeBufs").size());
            assertTrue(f.transport.sent.isEmpty());

            f.transport.blocked = false;
            f.inside.writable(f.context);
            assertEquals(1, f.transport.sent.size());
            assertArrayEquals(new byte[]{3, 4}, f.transport.sent.get(0));
            assertTrue(queue(f.fd, "writeBufs").isEmpty());

            f.transport.incoming = new byte[]{5, 6};
            f.inside.readable(f.context);
            f.transport.blocked = true;
            f.fd.write(ByteBuffer.wrap(new byte[]{7, 8}));
            f.inside.writable(f.context);
            f.fd.close();
            assertEquals(1, f.protocol.closes);
            assertFalse(f.transport.isOpen());
        }
    }

    @Test
    public void serverCloseStopsAcceptingAndRemovesMetric() throws Exception {
        try (Fixture f = new Fixture()) {
            StreamHandler handler = new StreamHandler();
            StreamedServerSocketFD[] pointer = new StreamedServerSocketFD[1];
            StreamedServerSocketFD server = new StreamedServerSocketFD(null, f.loop, new IPPort(28191), pointer);
            Object metric = get(server, "statisticsAcceptQueueLength");
            init(handler, f, new StreamConnectionStateCallback() {
                public void onReady(ArqUDPSocketFD fd) { }
                public void onInvalid(ArqUDPSocketFD fd) { }
                public boolean onAccept(StreamedFD fd) {
                    try {
                        call(server, "accepted", new Class<?>[]{StreamedFD.class}, fd);
                        return true;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            });
            try {
                assertTrue(handler.syn(1));
                assertTrue(handler.syn(3));
                handler.data(1, ByteArray.from(1, 2, 3));
                assertEquals(2, map(handler, "fdMap").size());
                server.close();
                assertNull(pointer[0]);
                assertThrows(IOException.class, server::accept);
                assertEquals(2, map(handler, "fdMap").size());
                assertFalse(metrics().contains(metric));
            } finally {
                server.close();
                call(handler, "clear", new Class<?>[0]);
            }
        }
    }

    private static void init(StreamHandler handler, Fixture f, StreamConnectionStateCallback callback) throws Exception {
        call(handler, "init", new Class<?>[]{ArqUDPSocketFD.class, SelectorEventLoop.class, StreamConnectionStateCallback.class}, f.fd, f.loop, callback);
        set(handler, "state", 2);
    }

    private static class StreamHandler extends H2StreamedFDHandler {
        boolean failFin;
        StreamHandler() { super(false); }
        boolean syn(int id) { return synReceived(id); }
        void data(int id, ByteArray bytes) { dataForStream(id, bytes); }
        void ack(long id) { keepaliveReceived(id, true); }
        @Override
        protected ByteArray formatFIN(int id) {
            if (failFin) throw new IllegalStateException("FIN failed");
            return super.formatFIN(id);
        }
    }

    private static class Fixture implements AutoCloseable {
        final SelectorEventLoop loop = SelectorEventLoop.open();
        final Transport transport = new Transport();
        final Protocol protocol;
        final ArqUDPSocketFD fd;
        final Handler<SocketFD> inside;
        final HandlerContext<SocketFD> context;

        Fixture() throws Exception {
            this(Protocol::new);
        }

        @SuppressWarnings("unchecked")
        Fixture(Function<Consumer<ByteArrayChannel>, ArqUDPHandler> factory) throws Exception {
            ArqUDPHandler[] holder = new ArqUDPHandler[1];
            fd = new ArqUDPSocketFD(transport, loop, emitter -> holder[0] = factory.apply(emitter)) { };
            protocol = holder[0] instanceof Protocol p ? p : null;
            loop.add(fd, EventSet.read(), null, new Handler<>() {
                public void accept(HandlerContext<ArqUDPSocketFD> ctx) { }
                public void connected(HandlerContext<ArqUDPSocketFD> ctx) { }
                public void readable(HandlerContext<ArqUDPSocketFD> ctx) { }
                public void writable(HandlerContext<ArqUDPSocketFD> ctx) { }
                public void removed(HandlerContext<ArqUDPSocketFD> ctx) { }
            });
            inside = (Handler<SocketFD>) get(fd, "fdHandler");
            var constructor = HandlerContext.class.getDeclaredConstructor(SelectorEventLoop.class);
            constructor.setAccessible(true);
            context = constructor.newInstance(loop);
            set(context, "channel", transport);
        }

        Runnable clock() throws Exception { return (Runnable) get(get(fd, "periodicEvent"), "runnable"); }

        public void close() throws Exception {
            try {
                fd.close();
            } finally {
                if (!loop.isClosed()) {
                    loop.remove(fd);
                    loop.close();
                }
            }
        }
    }

    private static class Protocol extends ArqUDPHandler {
        int writable = 1024;
        volatile int closes;
        int clocks;
        boolean failClose;
        String pause;
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch resume = new CountDownLatch(1);
        Protocol(Consumer<ByteArrayChannel> emitter) { super(emitter); }
        private void pause(String operation) throws IOException {
            if (!operation.equals(pause)) return;
            entered.countDown();
            try {
                if (!resume.await(5, TimeUnit.SECONDS)) throw new IOException("test callback timed out");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            }
        }
        public ByteArray parse(ByteArrayChannel input, int receiveLimit) throws IOException {
            pause("parse");
            return input.readAll();
        }
        public ByteArray receive(int receiveLimit) {
            return null;
        }
        public void write(ByteArray input) throws IOException {
            pause("write");
            emitter.accept(ByteArrayChannel.fromFull(input));
        }
        public int writableLen() { return writable; }
        public void clock(long ts) throws IOException {
            pause("clock");
            ++clocks;
            emitter.accept(ByteArrayChannel.fromFull(ByteArray.from(42)));
        }
        public int clockInterval() { return 10; }
        public void close() {
            ++closes;
            if (failClose) throw new IllegalStateException("protocol close failed");
        }
    }

    private static class Transport implements SocketFD, VirtualFD {
        boolean open = true;
        boolean blocked;
        int blockedWrites;
        byte[] incoming;
        final List<byte[]> sent = new ArrayList<>();
        public int read(ByteBuffer dst) {
            if (incoming == null) return 0;
            int len = incoming.length;
            dst.put(incoming);
            incoming = null;
            return len;
        }
        public int write(ByteBuffer src) {
            if (blocked) { ++blockedWrites; return 0; }
            byte[] bytes = new byte[src.remaining()];
            src.get(bytes);
            sent.add(bytes);
            return bytes.length;
        }
        public void connect(IPPort remote) { }
        public boolean isConnected() { return true; }
        public boolean finishConnect() { return true; }
        public void shutdownOutput() { }
        public IPPort getLocalAddress() { return new IPPort("127.0.0.1", 28192); }
        public IPPort getRemoteAddress() { return new IPPort("127.0.0.1", 28193); }
        public void configureBlocking(boolean b) { }
        public <T> void setOption(SocketOption<T> option, T value) { }
        public FD real() { return this; }
        public boolean contains(FD fd) { return fd == this; }
        public boolean isOpen() { return open; }
        public void close() { open = false; }
        public void onRegister() { }
        public void onRemove() { }
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }

    private static void assertThrows(Class<? extends Throwable> type, ThrowingAction action) throws Exception {
        try {
            action.run();
        } catch (Throwable t) {
            if (type.isInstance(t)) return;
            throw new AssertionError("expected " + type + ", got " + t, t);
        }
        fail("expected " + type.getName());
    }

    private static Field field(Object object, String name) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    private static Object get(Object object, String name) throws Exception { return field(object, name).get(object); }
    private static void set(Object object, String name, Object value) throws Exception { field(object, name).set(object, value); }
    private static Collection<?> queue(Object object, String name) throws Exception { return (Collection<?>) get(object, name); }
    @SuppressWarnings("unchecked")
    private static Map<Object, Object> map(Object object, String name) throws Exception { return (Map<Object, Object>) get(object, name); }
    private static Set<?> metrics() throws Exception { return (Set<?>) get(get(GlobalInspection.getInstance(), "metrics"), "metrics"); }
    private static Object call(Object object, String name, Class<?>[] types, Object... args) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Method method = type.getDeclaredMethod(name, types);
                method.setAccessible(true);
                return method.invoke(object, args);
            } catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException(name);
    }
}

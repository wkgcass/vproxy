package io.vproxy.test.cases;

import io.vproxy.base.Config;
import io.vproxy.base.connection.*;
import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.selector.wrap.VirtualFD;
import io.vproxy.base.util.ByteBufferEx;
import io.vproxy.base.util.RingBuffer;
import io.vproxy.base.util.ringbuffer.AbstractUnwrapByteBufferRingBuffer;
import io.vproxy.vfd.*;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import static org.junit.Assert.*;

public class TestConnectionReadFairness {
    static final int BUDGET = 32 * 1024;

    static class Input implements SocketFD, VirtualFD {
        final SelectorEventLoop loop;
        final TestTunReadFairness.ClockFDs clock;
        final int total;
        final byte[] payload;
        int sent, reads, pauseAt = -1, errorAt = -1;
        boolean open = true, posted;
        int taskAt = -1;
        final IOException error = new IOException("read reset fixture");
        Input(SelectorEventLoop loop, TestTunReadFairness.ClockFDs clock, int total) {
            this.loop = loop; this.clock = clock; this.total = total;
            payload = new byte[total];
            new java.util.Random(0x626f756e646564L).nextBytes(payload);
        }
        public int read(ByteBuffer dst) throws IOException {
            ++reads;
            if (sent == errorAt) throw error;
            if (sent == pauseAt) {
                pauseAt = -1;
                loop.selector.removeVirtualReadable(this);
                return 0;
            }
            if (sent == total) return -1;
            int n = Math.min(dst.remaining(), total - sent);
            if (pauseAt > sent) n = Math.min(n, pauseAt - sent);
            if (errorAt > sent) n = Math.min(n, errorAt - sent);
            for (int i = 0; i < n; ++i) dst.put(payload[sent++]);
            clock.nanos += (long) n * 8_000;
            if (!posted) { posted = true; loop.nextTick(() -> taskAt = sent); }
            return n;
        }
        public int write(ByteBuffer src) { int n = src.remaining(); src.position(src.limit()); return n; }
        public boolean isConnected() { return true; }
        public boolean finishConnect() { return true; }
        public void connect(IPPort addr) { }
        public void shutdownOutput() { }
        public IPPort getLocalAddress() { return new IPPort("192.0.2.1", 12345); }
        public IPPort getRemoteAddress() { return new IPPort("192.0.2.2", 7777); }
        public boolean isOpen() { return open; }
        public void close() { open = false; }
        public void configureBlocking(boolean b) { }
        public <T> void setOption(SocketOption<T> key, T value) { }
        public FD real() { return this; }
        public boolean contains(FD fd) { return fd == this; }
        public void onRegister() { loop.selector.registerVirtualReadable(this); }
        public void onRemove() { loop.selector.removeVirtualReadable(this); }
    }

    static class Fixture implements AutoCloseable {
        final long oldTimestamp = Config.currentTimestamp;
        final TestTunReadFairness.ClockFDs clock = new TestTunReadFairness.ClockFDs();
        final SelectorEventLoop loop = SelectorEventLoop.open(clock);
        final NetEventLoop net = new NetEventLoop(loop);
        final Input input;
        final RingBuffer plain = RingBuffer.allocate(256);
        final AbstractUnwrapByteBufferRingBuffer unwrap;
        final Connection conn;
        final ByteArrayOutputStream received = new ByteArrayOutputStream();
        int timerAt = -1, remoteClosed, exceptions;
        IOException lastException;
        boolean drain = true;
        Fixture(int total) throws Exception {
            loop.setBeforePoll(() -> 0);
            input = new Input(loop, clock, total);
            unwrap = new AbstractUnwrapByteBufferRingBuffer((io.vproxy.base.util.ringbuffer.ByteBufferRingBuffer) plain) {
                protected void handleEncryptedBuffer(ByteBufferEx in, boolean[] underflow, boolean[] errored, IOException[] error) {
                    ByteBuffer decoded = ByteBuffer.allocate(in.limit() - in.position());
                    decoded.put(in.realBuffer()).flip();
                    recordIntermediateBuffer(decoded);
                }
            };
            conn = Connection.wrap(input, new ConnectionOpts().setTimeout(60_000), unwrap, RingBuffer.allocate(8));
            net.addConnection(conn, null, new ConnectionHandler() {
                public void readable(ConnectionHandlerContext ctx) { if (drain) drainPlain(); }
                public void writable(ConnectionHandlerContext ctx) { }
                public void remoteClosed(ConnectionHandlerContext ctx) {
                    assertEquals("EOF after all tail data", input.total, received.size());
                    ++remoteClosed;
                }
                public void exception(ConnectionHandlerContext ctx, IOException e) { ++exceptions; lastException = e; }
                public void closed(ConnectionHandlerContext ctx) { }
                public void removed(ConnectionHandlerContext ctx) { }
            });
            loop.delay(5, () -> timerAt = input.sent);
        }
        void drainPlain() {
            while (plain.used() != 0) {
                try { plain.writeTo(src -> { int n = src.remaining(); while (src.hasRemaining()) received.write(src.get()); return n; }); }
                catch (IOException e) { throw new AssertionError(e); }
            }
        }
        void finish() throws Exception {
            for (int i = 0; i < 2048 && remoteClosed == 0 && exceptions == 0; ++i) loop.onePoll();
            assertEquals(1, remoteClosed);
            assertEquals(0, exceptions);
            assertArrayEquals(input.payload, received.toByteArray());
        }
        public void close() throws Exception { conn.close(); loop.close(); Config.currentTimestamp = oldTimestamp; }
    }

    @Test public void continuousTransportYieldsToTasksAndTimersBeforeDraining() throws Exception {
        try (var f = new Fixture(8 * BUDGET + 17)) {
            f.loop.onePoll();
            assertEquals("one callback transport quota", BUDGET, f.input.sent);
            assertEquals(BUDGET, f.received.size());
            f.loop.onePoll();
            assertEquals(BUDGET, f.input.taskAt);
            assertEquals(BUDGET, f.timerAt);
            f.finish();
        }
    }

    @Test public void eofIsNotInventedWhenBudgetEndsAtTheLastByte() throws Exception {
        try (var f = new Fixture(BUDGET)) {
            f.loop.onePoll();
            assertEquals(BUDGET, f.received.size());
            assertEquals("a budget yield is not EOF", 0, f.remoteClosed);
            f.finish();
            f.loop.onePoll();
            assertEquals("EOF exactly once", 1, f.remoteClosed);
        }
    }

    @Test public void eagainClearsReadinessAndRearmDeliversEveryByte() throws Exception {
        try (var f = new Fixture(2 * BUDGET + 3)) {
            f.input.pauseAt = BUDGET + 19;
            f.loop.onePoll(); f.loop.onePoll();
            assertEquals(BUDGET + 19, f.received.size());
            int reads = f.input.reads;
            f.loop.onePoll(); f.loop.onePoll();
            assertEquals("no spin after EAGAIN", reads, f.input.reads);
            f.loop.selector.registerVirtualReadable(f.input);
            f.finish();
        }
    }

    @Test public void resetPreservesDecodedTailBeforeExceptionCallback() throws Exception {
        try (var f = new Fixture(2 * BUDGET + 3)) {
            f.input.errorAt = BUDGET + 19;
            f.loop.onePoll();
            assertEquals(BUDGET, f.received.size());
            for (int i = 0; i < 8 && f.exceptions == 0; ++i) f.loop.onePoll();
            assertEquals(BUDGET + 19, f.received.size());
            assertEquals(1, f.exceptions);
            assertSame(f.input.error, f.lastException);
            assertEquals(0, f.remoteClosed);
        }
    }

    @Test public void slowConsumerDoesNotLoseStagedTailOrReportEofEarly() throws Exception {
        try (var f = new Fixture(BUDGET + 17)) {
            f.drain = false;
            f.loop.onePoll();
            assertEquals(BUDGET, f.input.sent);
            assertEquals(0, f.remoteClosed);
            f.drainPlain();
            f.drain = true;
            f.finish();
        }
    }
    static class BudgetAccess {
        final Object value;
        final java.lang.reflect.Method reset, release, read;
        BudgetAccess() throws Exception {
            var type = Class.forName("io.vproxy.base.connection.BoundedReadableByteStream");
            var ctor = type.getDeclaredConstructor(); ctor.setAccessible(true); value = ctor.newInstance();
            reset = type.getDeclaredMethod("reset", ReadableByteStream.class, int.class); reset.setAccessible(true);
            release = type.getDeclaredMethod("release"); release.setAccessible(true);
            read = type.getDeclaredMethod("read", ByteBuffer.class); read.setAccessible(true);
        }
        int read(ByteBuffer dst) throws Throwable {
            try { return (Integer) read.invoke(value, dst); }
            catch (java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
        }
    }

    @Test public void partialReadRestoresLimitAndPreservesMarkAfterException() throws Throwable {
        var a = new BudgetAccess();
        var expected = new IOException("partial reset");
        a.reset.invoke(a.value, (ReadableByteStream) dst -> {
            assertEquals(8, dst.limit());
            dst.put((byte) 1).put((byte) 2).put((byte) 3);
            throw expected;
        }, 5);
        var dst = ByteBuffer.allocate(20).position(3).limit(16).mark();
        try { a.read(dst); fail(); } catch (IOException e) { assertSame(expected, e); }
        assertEquals(16, dst.limit()); assertEquals(6, dst.position());
        dst.reset(); assertEquals(3, dst.position());
        assertEquals(1, dst.get(3)); assertEquals(2, dst.get(4)); assertEquals(3, dst.get(5));
        a.release.invoke(a.value);
        var field = a.value.getClass().getDeclaredField("source"); field.setAccessible(true);
        assertNull("do not retain a failed FD", field.get(a.value));
    }

    @Test public void zeroQuotaDoesNotProbeEofOrInvokeUnderlyingStream() throws Throwable {
        var a = new BudgetAccess(); int[] calls = {0};
        a.reset.invoke(a.value, (ReadableByteStream) dst -> { ++calls[0]; return -1; }, 0);
        assertEquals(0, a.read(ByteBuffer.allocate(8)));
        assertEquals(0, calls[0]);
        a.reset.invoke(a.value, (ReadableByteStream) dst -> { ++calls[0]; return -1; }, 8);
        assertEquals(-1, a.read(ByteBuffer.allocate(8)));
        assertEquals(1, calls[0]);
    }

    @Test public void invalidLimitMutationStillFailsAndReadonlyExceptionIsPreserved() throws Throwable {
        var a = new BudgetAccess();
        a.reset.invoke(a.value, (ReadableByteStream) dst -> { dst.limit(dst.limit() - 1); return 0; }, 5);
        var dst = ByteBuffer.allocate(20).position(3).limit(16);
        try { a.read(dst); fail(); } catch (IllegalStateException expected) { }
        assertEquals(16, dst.limit()); assertEquals(3, dst.position());
        a.reset.invoke(a.value, (ReadableByteStream) b -> { b.put((byte) 1); return 1; }, 5);
        var readonly = dst.asReadOnlyBuffer();
        try { a.read(readonly); fail(); } catch (java.nio.ReadOnlyBufferException expected) { }
        assertEquals(16, readonly.limit()); assertEquals(3, readonly.position());
    }

    @Test public void reentrantReadsUseIndependentQuotasAndReleaseBothSources() throws Exception {
        try (var f = new Fixture(0)) {
            var method = NetEventLoop.class.getDeclaredMethod("readToBuffer", RingBuffer.class, SocketFD.class);
            method.setAccessible(true);
            var nested = RingBuffer.allocate(BUDGET + 7);
            var outer = RingBuffer.allocate(BUDGET + 7);
            var innerInput = new Input(f.loop, f.clock, BUDGET + 7);
            var outerInput = new Input(f.loop, f.clock, BUDGET + 7) {
                boolean entered;
                public int read(ByteBuffer dst) throws IOException {
                    if (!entered) {
                        entered = true;
                        try { assertEquals(BUDGET, method.invoke(f.net, nested, innerInput)); }
                        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
                    }
                    return super.read(dst);
                }
            };
            try {
                assertEquals(BUDGET, method.invoke(f.net, outer, outerInput));
                assertEquals(BUDGET, outer.used()); assertEquals(BUDGET, nested.used());
                var field = NetEventLoop.class.getDeclaredField("readStream"); field.setAccessible(true);
                Object stream = field.get(f.net);
                var source = stream.getClass().getDeclaredField("source"); source.setAccessible(true);
                assertNull(source.get(stream));
            } finally { nested.clean(); outer.clean(); }
        }
    }

}

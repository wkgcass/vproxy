package io.vproxy.test.cases;

import io.vproxy.base.Config;
import io.vproxy.base.connection.*;
import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.ByteBufferEx;
import io.vproxy.base.util.RingBuffer;
import io.vproxy.base.util.nio.ByteArrayChannel;
import io.vproxy.base.util.ringbuffer.AbstractUnwrapByteBufferRingBuffer;
import io.vproxy.base.util.ringbuffer.ByteBufferRingBuffer;
import io.vproxy.vfd.Event;
import io.vproxy.vfd.EventSet;
import org.junit.Test;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import static org.junit.Assert.*;

public class TestConnectionReadBackpressure {
    static final byte[] PREFIX = {0, 1, 2, 3, 4, 5, 6, 7};
    static final byte[] OUTPUT = {-1, -2, -3};
    static class Decoder extends AbstractUnwrapByteBufferRingBuffer {
        Decoder(ByteBufferRingBuffer plain) { super(plain); }
        void bufferEncoded() throws IOException {
            encryptedBufferForInput.storeBytesFrom(ByteArrayChannel.fromFull(PREFIX));
        }
        void decodeBuffered() { generalUnwrap(); }
        protected void handleEncryptedBuffer(ByteBufferEx in, boolean[] underflow,
                                             boolean[] errored, IOException[] error) {
            var bytes = ByteBuffer.allocate(in.limit() - in.position());
            bytes.put(in.realBuffer()).flip();
            recordIntermediateBuffer(bytes);
        }
    }
    static class Fixture implements AutoCloseable {
        final long timestamp = Config.currentTimestamp;
        final TestTunReadFairness.ClockFDs clock = new TestTunReadFairness.ClockFDs();
        final SelectorEventLoop loop = SelectorEventLoop.open(clock);
        final NetEventLoop net = new NetEventLoop(loop);
        final ByteArrayOutputStream received = new ByteArrayOutputStream();
        final ByteArrayOutputStream written = new ByteArrayOutputStream();
        final ByteBufferRingBuffer plain = RingBuffer.allocate(PREFIX.length);
        final Decoder decoder;
        final RingBuffer in;
        final TestConnectionReadFairness.Input input;
        final Connection conn;
        int readable, remoteClosed, exceptions, connected;
        IOException lastException;
        boolean completedConnect;
        Fixture(boolean wrapped, boolean connecting, boolean pendingOutput) throws Exception {
            loop.setBeforePoll(() -> 0);
            decoder = wrapped ? new Decoder(plain) : null;
            in = wrapped ? decoder : plain;
            input = new TestConnectionReadFairness.Input(loop, clock, 37) {
                public boolean isConnected() { return !connecting || completedConnect; }
                public boolean finishConnect() { completedConnect = true; return true; }
                public void onRegister() {
                    super.onRegister(); loop.selector.registerVirtualWritable(this);
                }
                public void onRemove() {
                    super.onRemove(); loop.selector.removeVirtualWritable(this);
                }
                public int write(ByteBuffer src) {
                    int n = src.remaining();
                    while (src.hasRemaining()) written.write(src.get());
                    return n;
                }
            };
            var out = RingBuffer.allocate(8);
            var handler = new ConnectableConnectionHandler() {
                public void connected(ConnectableConnectionHandlerContext ctx) {
                    ++connected;
                    try {
                        fillInput();
                        if (pendingOutput) ctx.connection.runNoQuickWrite(c ->
                            c.getOutBuffer().storeBytesFrom(ByteArrayChannel.fromFull(OUTPUT)));
                    } catch (IOException e) { throw new AssertionError(e); }
                }
                public void readable(ConnectionHandlerContext ctx) { ++readable; drainInput(); }
                public void writable(ConnectionHandlerContext ctx) { }
                public void remoteClosed(ConnectionHandlerContext ctx) { ++remoteClosed; }
                public void exception(ConnectionHandlerContext ctx, IOException e) { ++exceptions; lastException = e; }
                public void closed(ConnectionHandlerContext ctx) { }
                public void removed(ConnectionHandlerContext ctx) { }
            };
            if (connecting) {
                conn = ConnectableConnection.wrap(input, input.getRemoteAddress(),
                    new ConnectionOpts().setTimeout(60_000), in, out);
                net.addConnectableConnection((ConnectableConnection) conn, null, handler);
            } else {
                conn = Connection.wrap(input, new ConnectionOpts().setTimeout(60_000), in, out);
                if (pendingOutput) out.storeBytesFrom(ByteArrayChannel.fromFull(OUTPUT));
                net.addConnection(conn, null, handler);
            }
        }
        void fillInput() throws IOException {
            if (decoder == null) plain.storeBytesFrom(ByteArrayChannel.fromFull(PREFIX));
            else decoder.bufferEncoded();
            assertEquals(0, in.free());
        }
        void drainInput() {
            try {
                in.writeTo(src -> {
                    int n = src.remaining();
                    while (src.hasRemaining()) received.write(src.get());
                    return n;
                });
            } catch (IOException e) { throw new AssertionError(e); }
        }
        void releaseInput() {
            if (decoder != null) decoder.decodeBuffered();
            drainInput();
            assertTrue("free-space edge re-arms READ", loop.getOps(input).have(Event.READABLE));
        }
        void finish() throws Exception {
            for (int i = 0; i < 128 && remoteClosed == 0; ++i) loop.onePoll();
            assertEquals(1, remoteClosed); assertEquals(0, exceptions);
            assertArrayEquals(ByteArray.from(PREFIX).concat(ByteArray.from(input.payload)).toJavaArray(), received.toByteArray());
            int reads = input.reads;
            for (int i = 0; i < 3; ++i) loop.onePoll();
            assertEquals("EOF does not spin", reads, input.reads); assertEquals(1, remoteClosed);
        }
        public void close() throws Exception {
            conn.close(); loop.close(); Config.currentTimestamp = timestamp;
        }
    }
    @Test public void aliasedFullInputSuspendsReadUntilConsumerMakesSpace() throws Exception {
        try (var f = new Fixture(false, false, false)) {
            // Public/shared input is filled after registration, with stale READ interest.
            f.fillInput(); f.loop.onePoll();
            assertFalse("full ring must not stay level-triggered", f.loop.getOps(f.input).have(Event.READABLE));
            assertEquals(0, f.input.reads); assertEquals(0, f.readable);
            for (int i = 0; i < 8; ++i) f.loop.onePoll();
            assertEquals(PREFIX.length, f.in.used()); assertEquals(0, f.remoteClosed);
            f.releaseInput(); f.finish();
        }
    }
    @Test public void decoderInputSuspendsAndResumesWithoutLosingStagedBytes() throws Exception {
        try (var f = new Fixture(true, false, false)) {
            f.fillInput(); f.loop.onePoll();
            assertFalse(f.loop.getOps(f.input).have(Event.READABLE)); assertEquals(0, f.input.reads);
            f.releaseInput(); f.finish();
        }
    }
    @Test public void removingReadInterestKeepsAlreadySelectedWriteProgress() throws Exception {
        try (var f = new Fixture(false, false, true)) {
            f.fillInput(); assertEquals(EventSet.readwrite(), f.loop.getOps(f.input)); f.loop.onePoll();
            assertFalse(f.loop.getOps(f.input).have(Event.READABLE));
            assertArrayEquals(OUTPUT, f.written.toByteArray()); assertEquals(0, f.conn.getOutBuffer().used());
            f.releaseInput(); f.finish();
        }
    }
    @Test public void connectingCallbackCannotEnableReadOnAnAlreadyFullRing() throws Exception {
        try (var f = new Fixture(false, true, true)) {
            f.loop.onePoll(); assertEquals(1, f.connected);
            assertFalse("connected callback filled input", f.loop.getOps(f.input).have(Event.READABLE));
            assertTrue("pending output still gets writable", f.loop.getOps(f.input).have(Event.WRITABLE));
            f.loop.onePoll(); assertArrayEquals(OUTPUT, f.written.toByteArray()); f.releaseInput(); f.finish();
        }
    }
    @Test public void blockedSelectedReadDoesNotPostponeIdleTimeout() throws Exception {
        try (var f = new Fixture(false, false, false)) {
            f.fillInput(); f.loop.onePoll();
            f.clock.nanos = 59_000_000_000L;
            f.loop.addOps(f.input, EventSet.read());
            f.loop.onePoll();
            assertEquals(0, f.input.reads);
            f.clock.nanos = 60_001_000_000L;
            f.loop.onePoll();
            assertEquals("no transport progress must not reset timeout", 1, f.exceptions);
            assertTrue(f.lastException instanceof java.net.SocketTimeoutException);
            assertEquals(0, f.remoteClosed);
        }
    }
}

package io.vproxy.test.cases;

import io.vproxy.base.Config;
import io.vproxy.base.connection.*;
import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.util.ByteBufferEx;
import io.vproxy.base.util.RingBuffer;
import io.vproxy.base.util.RingBufferETHandler;
import io.vproxy.base.util.nio.ByteArrayChannel;
import io.vproxy.base.util.ringbuffer.AbstractUnwrapByteBufferRingBuffer;
import io.vproxy.base.util.ringbuffer.ByteBufferRingBuffer;
import io.vproxy.vfd.Event;
import org.junit.Test;
import java.io.IOException;
import static org.junit.Assert.*;

public class TestDecoderErrorNotification {
    static class Decoder extends AbstractUnwrapByteBufferRingBuffer {
        final IOException failure = new IOException("authenticated decoder failure fixture");
        int decodeCalls;
        boolean allowReplacement;
        Decoder() { super(RingBuffer.allocate(8)); }
        void fill() { encryptedBufferForInput.storeBytesFrom(ByteArrayChannel.fromFull(new byte[8])); }
        void fail() { generalUnwrap(); }
        protected void handleEncryptedBuffer(ByteBufferEx in, boolean[] underflow,
                                             boolean[] errored, IOException[] error) {
            ++decodeCalls;
            errored[0] = true; error[0] = failure;
        }
        public RingBuffer switchBuffer(RingBuffer replacement) throws RingBuffer.RejectSwitchException {
            return allowReplacement ? replacement : super.switchBuffer(replacement);
        }
    }
    static class Fixture implements AutoCloseable {
        final long timestamp = Config.currentTimestamp;
        final TestTunReadFairness.ClockFDs clock = new TestTunReadFairness.ClockFDs();
        final SelectorEventLoop loop = SelectorEventLoop.open(clock);
        final NetEventLoop net = new NetEventLoop(loop);
        final Decoder decoder = new Decoder();
        final TestConnectionReadFairness.Input input;
        final Connection conn;
        int exceptions, closed, authenticatedBytesSeen;
        IOException lastError;
        Fixture(boolean ready, boolean failedBeforeRegistration) throws Exception {
            loop.setBeforePoll(() -> 0);
            input = new TestConnectionReadFairness.Input(loop, clock, 8) {
                public void onRegister() { if (ready) super.onRegister(); }
            };
            if (failedBeforeRegistration) { decoder.fill(); decoder.fail(); }
            conn = Connection.wrap(input, new ConnectionOpts().setTimeout(60_000), decoder, RingBuffer.allocate(8));
            net.addConnection(conn, null, new ConnectionHandler() {
                public void readable(ConnectionHandlerContext ctx) { authenticatedBytesSeen += ctx.connection.getInBuffer().used(); }
                public void writable(ConnectionHandlerContext ctx) { }
                public void remoteClosed(ConnectionHandlerContext ctx) { fail("decoder failure is not EOF"); }
                public void exception(ConnectionHandlerContext ctx, IOException error) { ++exceptions; lastError = error; }
                public void closed(ConnectionHandlerContext ctx) { ++closed; }
                public void removed(ConnectionHandlerContext ctx) { }
            });
        }
        void assertOneFailure() throws Exception {
            assertOneFailure(decoder.failure);
        }
        void assertOneFailure(IOException expected) throws Exception {
            loop.onePoll();
            assertEquals("error does not need another socket event or timeout", 1, exceptions);
            assertSame(expected, lastError);
            assertTrue(conn.isClosed()); assertEquals(1, closed);
            assertEquals(0, authenticatedBytesSeen);
            for (int i = 0; i < 4; ++i) loop.onePoll();
            assertEquals(1, exceptions); assertEquals(1, closed);
        }
        public void close() throws Exception {
            if (conn.getInBuffer() != decoder) decoder.clean();
            conn.close(); loop.close(); Config.currentTimestamp = timestamp;
        }
    }
    @Test public void savedFailureWhileReadIsSuspendedDoesNotWaitForTransport() throws Exception {
        try (var f = new Fixture(false, false)) {
            f.decoder.fill(); f.loop.selector.registerVirtualReadable(f.input); f.loop.onePoll();
            assertFalse(f.loop.getOps(f.input).have(Event.READABLE));
            f.loop.selector.removeVirtualReadable(f.input);
            f.decoder.fail(); f.decoder.fail();
            assertEquals("not reentrant inside decoder operation", 0, f.exceptions);
            f.assertOneFailure(); assertEquals(0, f.input.reads); assertEquals(1, f.decoder.decodeCalls);
        }
    }
    @Test public void decodeFailureDuringReadNeedsNoFurtherFdEvent() throws Exception {
        try (var f = new Fixture(true, false)) {
            f.loop.onePoll();
            assertEquals(1, f.input.reads);
            assertEquals(0, f.exceptions);
            f.loop.selector.removeVirtualReadable(f.input);
            f.assertOneFailure();
        }
    }
    @Test public void attachingAnAlreadyFailedFullDecoderReportsToItsOwner() throws Exception {
        try (var f = new Fixture(false, true)) {
            f.assertOneFailure(); assertEquals(0, f.input.reads);
        }
    }
    @Test public void removingOwnerBeforeDeliveryCancelsItsErrorCallback() throws Exception {
        try (var f = new Fixture(false, false)) {
            f.decoder.fill(); f.decoder.fail(); f.net.removeConnection(f.conn);
            for (int i = 0; i < 3; ++i) f.loop.onePoll();
            assertEquals(0, f.exceptions); assertEquals(0, f.closed); assertFalse(f.conn.isClosed());
        }
    }
    @Test public void replacingFailedInputBeforeDeliveryDoesNotCloseNewInput() throws Exception {
        try (var f = new Fixture(false, false)) {
            f.decoder.allowReplacement = true;
            f.decoder.fill(); f.decoder.fail();
            f.conn.replaceBuffer(RingBuffer.allocate(8), RingBuffer.allocate(8), false, false);
            for (int i = 0; i < 3; ++i) f.loop.onePoll();
            assertEquals(0, f.exceptions); assertFalse(f.conn.isClosed());
        }
    }
    @Test public void installingAnAlreadyFailedInputReportsWithoutFdReadiness() throws Exception {
        try (var f = new Fixture(false, false)) {
            var replacement = new Decoder();
            replacement.fill(); replacement.fail();
            f.decoder.allowReplacement = true;
            f.conn.replaceBuffer(replacement, RingBuffer.allocate(8), false, false);
            assertSame(replacement, f.conn.getInBuffer());
            assertEquals(0, f.exceptions);
            f.assertOneFailure(replacement.failure); assertEquals(0, f.input.reads);
        }
    }
    @Test public void reattachingOwnerCancelsOldDeliveryAndReplaysToNewContext() throws Exception {
        try (var f = new Fixture(false, false)) {
            f.decoder.fill(); f.decoder.fail(); f.net.removeConnection(f.conn);
            int[] newOwner = {0, 0};
            f.net.addConnection(f.conn, null, new ConnectionHandler() {
                public void readable(ConnectionHandlerContext ctx) { fail("failed input is not readable"); }
                public void writable(ConnectionHandlerContext ctx) { }
                public void remoteClosed(ConnectionHandlerContext ctx) { fail("decoder failure is not EOF"); }
                public void exception(ConnectionHandlerContext ctx, IOException error) {
                    assertSame(f.decoder.failure, error); ++newOwner[0];
                }
                public void closed(ConnectionHandlerContext ctx) { ++newOwner[1]; }
                public void removed(ConnectionHandlerContext ctx) { }
            });
            for (int i = 0; i < 4; ++i) f.loop.onePoll();
            assertEquals("previous owner must not be called", 0, f.exceptions);
            assertEquals(0, f.closed);
            assertArrayEquals(new int[]{1, 1}, newOwner); assertTrue(f.conn.isClosed());
        }
    }
    @Test public void observerReplacingInputDuringFailureCannotCloseNewInput() throws Exception {
        try (var f = new Fixture(false, false)) {
            var replacement = RingBuffer.allocate(8);
            f.decoder.allowReplacement = true;
            f.decoder.addHandler(new RingBufferETHandler() {
                public void readableET() { }
                public void writableET() { }
                public void exception(RingBuffer source, IOException error) {
                    assertSame(f.decoder, source);
                    try { f.conn.replaceBuffer(replacement, RingBuffer.allocate(8), false, false); }
                    catch (IOException e) { throw new AssertionError(e); }
                }
            });
            f.decoder.fill(); f.decoder.fail();
            assertSame(replacement, f.conn.getInBuffer());
            for (int i = 0; i < 4; ++i) f.loop.onePoll();
            assertEquals(0, f.exceptions); assertEquals(0, f.closed); assertFalse(f.conn.isClosed());
        }
    }
    @Test public void failingObserverDoesNotPreventOwnerNotification() throws Exception {
        try (var f = new Fixture(false, false)) {
            int[] calls = {0};
            f.decoder.addHandler(new RingBufferETHandler() {
                public void readableET() { }
                public void writableET() { }
                public void exception(RingBuffer source, IOException error) { ++calls[0]; throw new IllegalStateException("observer fixture"); }
            });
            f.decoder.fill(); f.decoder.fail();
            f.assertOneFailure(); assertEquals(1, calls[0]);
        }
    }
    @Test public void directBufferIoStillThrowsTheOriginalDecoderError() throws Exception {
        var d = new Decoder();
        try {
            d.fill(); d.fail();
            try { d.storeBytesFrom(dst -> { fail("failed decoder must not read transport"); return 0; }); fail(); }
            catch (IOException e) { assertSame(d.failure, e); }
            try { d.writeTo(src -> { fail("failed decoder must not release untrusted data"); return 0; }); fail(); }
            catch (IOException e) { assertSame(d.failure, e); }
        } finally { d.clean(); }
    }
}

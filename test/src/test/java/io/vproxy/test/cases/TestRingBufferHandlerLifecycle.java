package io.vproxy.test.cases;

import io.vproxy.base.connection.Connection;
import io.vproxy.base.connection.ConnectionOpts;
import io.vproxy.base.util.ByteBufferEx;
import io.vproxy.base.util.RingBuffer;
import io.vproxy.base.util.RingBufferETHandler;
import io.vproxy.base.util.ringbuffer.*;
import io.vproxy.vfd.ReadableByteStream;
import io.vproxy.vfd.WritableByteStream;
import io.vproxy.vswitch.stack.fd.*;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Set;

import static org.junit.Assert.*;

public class TestRingBufferHandlerLifecycle {
    static class Handler implements RingBufferETHandler {
        public void readableET() { }
        public void writableET() { }
    }

    static class BaseRing extends AbstractRingBuffer {
        final RingBuffer data = RingBuffer.allocate(8);
        void cycle(Runnable action) {
            setOperating(true);
            try { action.run(); } finally { setOperating(false); }
        }
        public int storeBytesFrom(ReadableByteStream in) throws IOException { return data.storeBytesFrom(in); }
        public int writeTo(WritableByteStream out, int n) throws IOException { return data.writeTo(out, n); }
        public int free() { return data.free(); }
        public int used() { return data.used(); }
        public int capacity() { return data.capacity(); }
        public void clean() { data.clean(); }
        public void clear() { data.clear(); }
    }

    private static RingBuffer ring(boolean base) { return base ? new BaseRing() : RingBuffer.allocate(8); }

    private static void cycle(RingBuffer ring, Runnable action) throws Exception {
        if (ring instanceof BaseRing b) b.cycle(action);
        else ((ByteBufferRingBuffer) ring).operateOnByteBufferStoreIn(_ -> { action.run(); return true; });
    }

    private static void assertDeferredSetsEmpty(RingBuffer ring) throws Exception {
        Class<?> type = ring instanceof BaseRing ? AbstractRingBuffer.class : SimpleRingBuffer.class;
        for (String name : new String[]{"handlerToAdd", "handlerToRemove"}) {
            var field = type.getDeclaredField(name);
            field.setAccessible(true);
            assertTrue("completed transaction must release " + name, ((Set<?>) field.get(ring)).isEmpty());
        }
    }

    @Test
    public void deferredAdditionDoesNotResurrectRemovedHandler() throws Exception {
        for (boolean base : new boolean[]{false, true}) {
            var ring = ring(base);
            try {
                var h = new Handler();
                cycle(ring, () -> ring.addHandler(h));
                assertTrue(ring.getHandlers().contains(h));
                ring.removeHandler(h);
                cycle(ring, () -> {});
                assertFalse(ring.getHandlers().contains(h));
                assertDeferredSetsEmpty(ring);
            } finally { ring.clean(); }
        }
    }

    @Test
    public void deferredRemovalDoesNotUndoLaterRegistration() throws Exception {
        for (boolean base : new boolean[]{false, true}) {
            var ring = ring(base);
            try {
                var h = new Handler();
                ring.addHandler(h);
                cycle(ring, () -> ring.removeHandler(h));
                assertFalse(ring.getHandlers().contains(h));
                ring.addHandler(h);
                cycle(ring, () -> {});
                assertTrue(ring.getHandlers().contains(h));
                assertDeferredSetsEmpty(ring);
            } finally { ring.clean(); }
        }
    }

    @Test
    public void repeatedHandoffsReleaseAllDeferredReferences() throws Exception {
        for (boolean base : new boolean[]{false, true}) {
            var ring = ring(base);
            try {
                for (int i = 0; i < 1000; ++i) {
                    var h = new Handler();
                    cycle(ring, () -> ring.addHandler(h));
                    ring.removeHandler(h);
                    assertTrue(ring.getHandlers().isEmpty());
                    assertDeferredSetsEmpty(ring);
                }
            } finally { ring.clean(); }
        }
    }

    @Test
    public void lastMutationWinsWithinOneOperation() throws Exception {
        for (boolean base : new boolean[]{false, true}) {
            var ring = ring(base);
            try {
                var keep = new Handler();
                var remove = new Handler();
                cycle(ring, () -> {
                    ring.addHandler(remove); ring.removeHandler(remove);
                    ring.removeHandler(keep); ring.addHandler(keep);
                    assertTrue("mutations remain deferred", ring.getHandlers().isEmpty());
                });
                assertEquals(Set.of(keep), ring.getHandlers());
                assertDeferredSetsEmpty(ring);
            } finally { ring.clean(); }
        }
    }

    @Test
    public void closedConnectionIsNotReattachedToReusedSharedBuffer() throws Exception {
        for (boolean base : new boolean[]{false, true}) {
            var ring = ring(base);
            var out = RingBuffer.allocate(8);
            try (var f = new TestTcpReset.Fixture(false, false, 12345)) {
                var ctx = new VSwitchFDContext(f.delegate, f.network);
                var fd = new VSwitchSocketFD(ctx, new VSwitchFDs(ctx), f.tcp) {};
                Connection[] owner = {null};
                try {
                    cycle(ring, () -> {
                        try { owner[0] = Connection.wrap(fd, ConnectionOpts.getDefault(), ring, out); }
                        catch (IOException e) { throw new RuntimeException(e); }
                    });
                    assertEquals(1, ring.getHandlers().size());
                    owner[0].closeKeepBuffers();
                    cycle(ring, () -> {});
                    assertTrue(owner[0].isClosed());
                    assertTrue("closed owner must not be retained by shared ring", ring.getHandlers().isEmpty());
                    assertDeferredSetsEmpty(ring);
                } finally { if (owner[0] != null) owner[0].closeKeepBuffers(); }
            } finally { ring.clean(); out.clean(); }
        }
    }

    private static void failingNotification(RingBuffer ring, boolean readable) throws Exception {
        var failure = new IllegalStateException("callback fixture");
        var replacement = new Handler();
        var throwing = new Handler() {
            private void failCallback() {
                ring.removeHandler(this);
                ring.addHandler(replacement);
                throw failure;
            }
            public void readableET() { if (readable) failCallback(); }
            public void writableET() { if (!readable) failCallback(); }
        };
        ring.addHandler(throwing);
        try {
            ring.storeBytesFrom(ByteBuffer.wrap(new byte[]{7}));
            fail("callback exception must propagate unchanged");
        } catch (IllegalStateException expected) { assertSame(failure, expected); }
        assertEquals(Set.of(replacement), ring.getHandlers());
        var field = (ring instanceof SimpleRingBuffer ? SimpleRingBuffer.class : AbstractRingBuffer.class).getDeclaredField("operating");
        field.setAccessible(true);
        assertFalse("failed callback must end its transaction", field.getBoolean(ring));
    }

    @Test
    public void simpleCallbackFailureStillCompletesDeferredMutations() throws Exception {
        var ring = RingBuffer.allocate(8);
        try { failingNotification(ring, true); } finally { ring.clean(); }
    }

    @Test
    public void wrapCallbackFailureStillCompletesDeferredMutations() throws Exception {
        var ring = new AbstractWrapByteBufferRingBuffer(RingBuffer.allocate(8)) {
            { transferring = true; }
            protected void handlePlainBuffer(ByteBufferEx input, boolean[] errored, IOException[] ex) {
                if (input.position() == input.limit()) return;
                var output = ByteBuffer.allocate(input.limit() - input.position());
                output.put(input.realBuffer()).flip();
                recordIntermediateBuffer(output);
            }
        };
        try { failingNotification(ring, true); } finally { ring.clean(); }
    }

    @Test
    public void unwrapCallbackFailureStillCompletesDeferredMutations() throws Exception {
        var ring = new AbstractUnwrapByteBufferRingBuffer(RingBuffer.allocate(8)) {
            protected void handleEncryptedBuffer(ByteBufferEx input, boolean[] underflow, boolean[] errored, IOException[] ex) {
                var output = ByteBuffer.allocate(input.limit() - input.position());
                output.put(input.realBuffer()).flip();
                recordIntermediateBuffer(output);
            }
        };
        try { failingNotification(ring, false); } finally { ring.clean(); }
    }
}

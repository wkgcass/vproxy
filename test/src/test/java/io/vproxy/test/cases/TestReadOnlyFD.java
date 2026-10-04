package io.vproxy.test.cases;

import io.vproxy.vfd.FD;
import io.vproxy.vfd.abs.AbstractBaseFD;
import io.vproxy.vfd.posix.Posix;
import io.vproxy.vfd.posix.PosixDatagramFD;
import io.vproxy.vfd.posix.PosixSocketFD;
import io.vproxy.vfd.IPPort;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.SocketOption;
import java.nio.ByteBuffer;

import static org.junit.Assert.*;

public class TestReadOnlyFD {
    static class Reader extends AbstractBaseFD {
        int calls;
        int read(ByteBuffer dst, int result) throws IOException {
            return utilRead(dst, (buffer, offset, length) -> {
                ++calls;
                if (result > 0) buffer.put(offset, (byte) 42);
                return result;
            });
        }
        int write(ByteBuffer src) throws IOException {
            return utilWrite(src, (buffer, offset, length) -> {
                assertEquals(42, buffer.get(offset));
                return 1;
            });
        }
        public boolean isOpen() { return true; }
        public void configureBlocking(boolean blocking) { }
        public <T> void setOption(SocketOption<T> option, T value) { }
        public FD real() { return this; }
        public boolean contains(FD fd) { return fd == this; }
    }

    @FunctionalInterface
    interface Action { void run() throws IOException; }

    static void rejects(Action action) throws IOException {
        try { action.run(); fail("must reject a read-only destination before I/O"); }
        catch (IllegalArgumentException expected) { }
    }

    static ByteBuffer destination(boolean direct, boolean empty) {
        var buffer = direct ? ByteBuffer.allocateDirect(8) : ByteBuffer.allocate(8);
        for (int i = 0; i < buffer.capacity(); ++i) buffer.put(i, (byte) 85);
        // Exercise an offset slice, not just a position-zero buffer.
        buffer.position(1).limit(7);
        var view = buffer.slice().asReadOnlyBuffer();
        view.position(2).limit(empty ? 2 : 5);
        return view;
    }

    private static void checkReadOnly(boolean direct, boolean empty) throws IOException {
        try (var fd = new Reader()) {
            var dst = destination(direct, empty);
            rejects(() -> fd.read(dst, empty ? 0 : 1));
            assertEquals(0, fd.calls);
            assertEquals(2, dst.position());
            assertEquals(empty ? 2 : 5, dst.limit());
            assertEquals(85, dst.duplicate().clear().get(2));
        }
    }

    @Test public void heapReadOnlyRejectedBeforeRead() throws IOException { checkReadOnly(false, false); }
    @Test public void directReadOnlyRejectedBeforeRead() throws IOException { checkReadOnly(true, false); }
    @Test public void emptyReadOnlyRejectedBeforeRead() throws IOException {
        checkReadOnly(false, true); checkReadOnly(true, true);
    }

    @Test public void writableReadStillPreservesWindowAndPartialProgress() throws IOException {
        for (boolean direct : new boolean[]{false, true}) {
            try (var fd = new Reader()) {
                var dst = direct ? ByteBuffer.allocateDirect(8) : ByteBuffer.allocate(8);
                dst.position(2).limit(6);
                assertEquals(1, fd.read(dst, 1));
                assertEquals(3, dst.position()); assertEquals(6, dst.limit());
                assertEquals(42, dst.get(2)); assertEquals(0, dst.get(1)); assertEquals(0, dst.get(3));
                assertEquals(0, fd.read(dst, 0)); assertEquals(3, dst.position());
                assertEquals(-1, fd.read(dst, -1)); assertEquals(3, dst.position());
            }
        }
    }

    @Test public void readOnlySourceStillSupportsPartialWrite() throws IOException {
        for (boolean direct : new boolean[]{false, true}) {
            try (var fd = new Reader()) {
                var source = direct ? ByteBuffer.allocateDirect(8) : ByteBuffer.allocate(8);
                source.put(2, (byte) 42).position(2).limit(6);
                var src = source.asReadOnlyBuffer();
                assertEquals(1, fd.write(src));
                assertEquals(3, src.position()); assertEquals(6, src.limit());
                assertEquals(42, source.get(2));
            }
        }
    }

    static class NativeCalls {
        int reads;
        final Posix posix = (Posix) Proxy.newProxyInstance(Posix.class.getClassLoader(), new Class[]{Posix.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "createIPv4UdpFD" -> 41;
                case "read", "readBlocking" -> { ++reads; yield 0; }
                case "recvfromIPv4" -> { ++reads; yield null; }
                case "bindIPv4", "close" -> null;
                default -> throw new AssertionError("unexpected native operation " + method.getName());
            });
    }

    @Test public void tcpReadAndBlockingReadRejectBeforeNativeCall() throws IOException {
        var calls = new NativeCalls();
        try (var fd = new PosixSocketFD(calls.posix, 41, true)) {
            for (boolean direct : new boolean[]{false, true}) {
                for (boolean empty : new boolean[]{false, true}) {
                    rejects(() -> fd.read(destination(direct, empty)));
                    rejects(() -> fd.readBlocking(destination(direct, empty)));
                }
            }
            assertEquals(0, calls.reads);
        }
    }

    @Test public void datagramReceiveRejectsBeforeNativeCall() throws IOException {
        var calls = new NativeCalls();
        try (var fd = new PosixDatagramFD(calls.posix)) {
            fd.bind(new IPPort("127.0.0.1", 0));
            for (boolean direct : new boolean[]{false, true}) {
                for (boolean empty : new boolean[]{false, true}) {
                    rejects(() -> fd.receive(destination(direct, empty)));
                }
            }
            assertEquals(0, calls.reads);
        }
    }

    @Test public void existingSocketStateErrorsStillPrecedeBufferValidation() throws IOException {
        var calls = new NativeCalls();
        var fd = new PosixSocketFD(calls.posix, 41, true);
        fd.close();
        try { fd.read(destination(false, false)); fail(); }
        catch (IOException expected) { assertEquals("closed", expected.getMessage()); }
        try (var unbound = new PosixDatagramFD(calls.posix)) {
            try { unbound.receive(destination(true, false)); fail(); }
            catch (IOException expected) { assertEquals("connect() or bind() not called", expected.getMessage()); }
        }
        assertEquals(0, calls.reads);
    }
}

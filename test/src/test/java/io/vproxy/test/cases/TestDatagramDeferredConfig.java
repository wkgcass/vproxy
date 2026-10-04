package io.vproxy.test.cases;

import io.vproxy.vfd.IPPort;
import io.vproxy.vfd.posix.Posix;
import io.vproxy.vfd.posix.PosixDatagramFD;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class TestDatagramDeferredConfig {
    static class NativeCalls {
        final List<String> calls = new ArrayList<>();
        final IOException configError = new IOException("fixture configuration");
        final IOException closeError = new IOException("fixture close");
        boolean failConfig, failClose;
        int nextFD = 41;
        final Posix posix = (Posix) Proxy.newProxyInstance(Posix.class.getClassLoader(), new Class[]{Posix.class},
            (proxy, method, args) -> {
                String name = method.getName();
                calls.add(name);
                return switch (name) {
                    case "createIPv4UdpFD", "createIPv6UdpFD" -> nextFD++;
                    case "setBlocking" -> {
                        assertEquals(false, args[1]);
                        if (failConfig) throw configError;
                        yield null;
                    }
                    case "setBroadcast" -> { assertEquals(true, args[1]); yield null; }
                    case "setRcvBuf" -> { assertEquals(4096, args[1]); yield null; }
                    case "sendtoIPv4", "sendtoIPv6" -> args[3];
                    case "close" -> { if (failClose) throw closeError; yield null; }
                    default -> throw new AssertionError("unexpected native operation " + name);
                };
            });
    }

    private static void configAppliedOnce(boolean ipv6) throws IOException {
        var nativeCalls = new NativeCalls();
        try (var fd = new PosixDatagramFD(nativeCalls.posix)) {
            fd.configureBlocking(false);
            fd.setOption(StandardSocketOptions.SO_BROADCAST, true);
            fd.setOption(StandardSocketOptions.SO_RCVBUF, 4096);
            assertTrue(nativeCalls.calls.isEmpty());
            var remote = new IPPort(ipv6 ? "::1" : "127.0.0.1", 12345);
            var src = ByteBuffer.wrap(new byte[]{0, 7, 19, 0}).position(1).limit(3).asReadOnlyBuffer();
            assertEquals(2, fd.send(src, remote));
            assertEquals(3, src.position()); assertEquals(3, src.limit());
            assertEquals(ipv6 ? "createIPv6UdpFD" : "createIPv4UdpFD", nativeCalls.calls.getFirst());
            String send = ipv6 ? "sendtoIPv6" : "sendtoIPv4";
            int sendIndex = nativeCalls.calls.indexOf(send);
            for (String setter : new String[]{"setBlocking", "setBroadcast", "setRcvBuf"}) {
                assertTrue("apply " + setter + " before first transmission", nativeCalls.calls.indexOf(setter) > 0);
                assertTrue(nativeCalls.calls.indexOf(setter) < sendIndex);
            }
            int size = nativeCalls.calls.size();
            assertEquals(1, fd.send(ByteBuffer.wrap(new byte[]{31}), remote));
            assertEquals(List.of(send), nativeCalls.calls.subList(size, nativeCalls.calls.size()));
        }
        assertEquals("close", nativeCalls.calls.getLast());
    }

    @Test public void ipv4FirstSendAppliesDeferredConfigurationOnce() throws IOException { configAppliedOnce(false); }
    @Test public void ipv6FirstSendAppliesDeferredConfigurationOnce() throws IOException { configAppliedOnce(true); }

    @Test public void unconfiguredSendRetainsItsExistingBehavior() throws IOException {
        var calls = new NativeCalls();
        try (var fd = new PosixDatagramFD(calls.posix)) {
            assertEquals(1, fd.send(ByteBuffer.wrap(new byte[]{7}), new IPPort("127.0.0.1", 12345)));
            assertEquals(List.of("createIPv4UdpFD", "sendtoIPv4"), calls.calls);
        }
    }

    @Test public void failedConfigurationClosesDescriptorWithoutConsumingSourceAndCanRetry() throws IOException {
        var calls = new NativeCalls();
        try (var fd = new PosixDatagramFD(calls.posix)) {
            fd.configureBlocking(false);
            fd.setOption(StandardSocketOptions.SO_BROADCAST, true);
            calls.failConfig = true;
            var src = ByteBuffer.wrap(new byte[]{7});
            try { fd.send(src, new IPPort("127.0.0.1", 12345)); fail(); }
            catch (IOException expected) { assertSame(calls.configError, expected); }
            assertEquals(-1, fd.getFD()); assertTrue(fd.isOpen()); assertEquals(0, src.position());
            assertEquals(List.of("createIPv4UdpFD", "setBlocking", "close"), calls.calls);
            calls.failConfig = false;
            calls.calls.clear();
            assertEquals(1, fd.send(src, new IPPort("::1", 12345)));
            assertEquals(42, fd.getFD());
            assertEquals(List.of("createIPv6UdpFD", "setBlocking", "setBroadcast", "sendtoIPv6"), calls.calls);
        }
    }

    @Test public void invalidDeferredValueAlsoReleasesTheNewDescriptor() throws IOException {
        var calls = new NativeCalls();
        try (var fd = new PosixDatagramFD(calls.posix)) {
            fd.setOption(StandardSocketOptions.SO_BROADCAST, null);
            var src = ByteBuffer.wrap(new byte[]{7});
            try { fd.send(src, new IPPort("127.0.0.1", 12345)); fail(); }
            catch (NullPointerException expected) { }
            assertEquals(-1, fd.getFD()); assertEquals(0, src.position());
            assertEquals(List.of("createIPv4UdpFD", "close"), calls.calls);
            fd.setOption(StandardSocketOptions.SO_BROADCAST, true);
            assertEquals(1, fd.send(src, new IPPort("127.0.0.1", 12345)));
        }
    }

    @Test public void closeFailureDoesNotHideTheConfigurationError() throws IOException {
        var calls = new NativeCalls();
        try (var fd = new PosixDatagramFD(calls.posix)) {
            fd.configureBlocking(false);
            calls.failConfig = true; calls.failClose = true;
            try { fd.send(ByteBuffer.wrap(new byte[]{7}), new IPPort("127.0.0.1", 12345)); fail(); }
            catch (IOException expected) {
                assertSame(calls.configError, expected);
                assertArrayEquals(new Throwable[]{calls.closeError}, expected.getSuppressed());
            }
            assertEquals(-1, fd.getFD());
            calls.failClose = false;
        }
    }
}

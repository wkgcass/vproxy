package io.vproxy.test.cases;

import io.vproxy.vfd.IPPort;
import io.vproxy.vfd.posix.*;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class TestDatagramLifecycle {
    static class NativeCalls {
        final Set<Integer> open = new HashSet<>();
        int next = 41, creates, localPort, receives, broadcasts;
        boolean failBind, failSend, autoBind = true;
        int sendResult = -1;
        final Posix posix = (Posix) Proxy.newProxyInstance(Posix.class.getClassLoader(), new Class[]{Posix.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "createIPv4UdpFD", "createIPv6UdpFD" -> { ++creates; open.add(next); yield next++; }
                case "close" -> { assertTrue(open.remove((Integer) args[0])); yield null; }
                case "setBlocking" -> null;
                case "setBroadcast" -> { assertEquals(true, args[1]); ++broadcasts; yield null; }
                case "bindIPv4", "bindIPv6" -> {
                    if (failBind) throw new IOException("fixture bind failed");
                    localPort = (Integer) args[2]; yield null;
                }
                case "connectIPv4", "connectIPv6" -> { localPort = 23456; yield null; }
                case "sendtoIPv4", "sendtoIPv6" -> {
                    if (failSend) throw new IOException("fixture send failed");
                    if ((Integer) args[3] > 0 && autoBind) localPort = 23456;
                    yield sendResult >= 0 ? sendResult : args[3];
                }
                case "getIPv4Local" -> new SocketAddressIPv4(0x7f000001, localPort);
                case "getIPv6Local" -> new SocketAddressIPv6("::1", localPort);
                case "recvfromIPv4", "recvfromIPv6" -> {
                    ++receives;
                    ((ByteBuffer) args[1]).put((Integer) args[2], (byte) 42);
                    yield new UDPRecvResult(new SocketAddressIPv4(0x7f000001, 12345), 1);
                }
                default -> throw new AssertionError("unexpected native call " + method.getName());
            });
    }

    @Test public void successfulSendPermitsReplyAndRejectsRebindWithoutReplacingFd() throws IOException {
        for (boolean ipv6 : new boolean[]{false, true}) {
            var calls = new NativeCalls();
            try (var fd = new PosixDatagramFD(calls.posix)) {
                var address = new IPPort(ipv6 ? "::1" : "127.0.0.1", 12345);
                assertEquals(1, fd.send(ByteBuffer.wrap(new byte[]{7}), address));
                int original = fd.getFD();
                try { fd.bind(address); fail("successful send already assigned a local port"); }
                catch (IOException expected) { }
                assertEquals(original, fd.getFD()); assertEquals(1, calls.creates);
                var dst = ByteBuffer.allocate(4).position(1).limit(3);
                assertNotNull(fd.receive(dst)); assertEquals(2, dst.position()); assertEquals(3, dst.limit());
                assertEquals(42, dst.get(1));
            }
            assertTrue(calls.open.isEmpty());
        }
    }

    @Test public void connectedDatagramPermitsReceiveWithoutExplicitBind() throws IOException {
        var calls = new NativeCalls();
        try (var fd = new PosixDatagramFD(calls.posix)) {
            fd.connect(new IPPort("127.0.0.1", 12345));
            var dst = ByteBuffer.allocate(4);
            try { assertNotNull(fd.receive(dst)); }
            catch (IOException e) { throw new AssertionError("connected socket can receive from its peer", e); }
            assertEquals(1, dst.position()); assertEquals(42, dst.get(0));
        }
        assertTrue(calls.open.isEmpty());
    }

    @Test public void failedBindRetryKeepsFdOptionsAndRefreshesCachedLocalAddress() throws IOException {
        var calls = new NativeCalls();
        try (var fd = new PosixDatagramFD(calls.posix)) {
            fd.setOption(StandardSocketOptions.SO_BROADCAST, true);
            calls.failBind = true;
            var address = new IPPort("127.0.0.1", 12345);
            try { fd.bind(address); fail(); }
            catch (IOException e) { assertEquals("fixture bind failed", e.getMessage()); }
            int original = fd.getFD();
            assertEquals(0, fd.getLocalAddress().getPort());
            calls.failBind = false;
            fd.bind(address);
            assertEquals(original, fd.getFD()); assertEquals(1, calls.creates); assertEquals(1, calls.broadcasts);
            assertEquals(12345, fd.getLocalAddress().getPort());
        }
        assertTrue(calls.open.isEmpty());
    }

    @Test public void bindConfigurationFailureReleasesFdAndAllowsCorrection() throws IOException {
        var calls = new NativeCalls();
        try (var fd = new PosixDatagramFD(calls.posix)) {
            fd.setOption(StandardSocketOptions.SO_BROADCAST, null);
            try { fd.bind(new IPPort("127.0.0.1", 12345)); fail(); }
            catch (NullPointerException expected) { }
            assertEquals(-1, fd.getFD()); assertTrue(calls.open.isEmpty());
            fd.setOption(StandardSocketOptions.SO_BROADCAST, true);
            fd.bind(new IPPort("127.0.0.1", 12345));
            assertEquals(1, calls.broadcasts);
        }
        assertTrue(calls.open.isEmpty());
    }

    @Test public void retryCannotReplaceAllocatedFdWithAnotherAddressFamily() throws IOException {
        var calls = new NativeCalls();
        try (var fd = new PosixDatagramFD(calls.posix)) {
            calls.failBind = true;
            try { fd.bind(new IPPort("127.0.0.1", 12345)); fail(); }
            catch (IOException expected) { }
            int original = fd.getFD();
            calls.failBind = false;
            try { fd.bind(new IPPort("::1", 12345)); fail("cannot replace an allocated IPv4 fd"); }
            catch (IOException expected) { }
            assertEquals(original, fd.getFD()); assertEquals(1, calls.creates);
        }
        assertTrue(calls.open.isEmpty());
    }

    @Test public void failedSendFollowedByBindReusesTheOwnedFd() throws IOException {
        var calls = new NativeCalls();
        try (var fd = new PosixDatagramFD(calls.posix)) {
            calls.failSend = true;
            var src = ByteBuffer.wrap(new byte[]{7});
            try { fd.send(src, new IPPort("127.0.0.1", 12345)); fail(); }
            catch (IOException expected) { }
            int original = fd.getFD();
            assertEquals(0, src.position());
            calls.failSend = false;
            fd.bind(new IPPort("127.0.0.1", 12345));
            assertEquals(original, fd.getFD()); assertEquals(1, calls.creates);
        }
        assertTrue(calls.open.isEmpty());
    }

    @Test public void emptySendRemainsUnboundAndBindReusesFd() throws IOException {
        var calls = new NativeCalls();
        try (var fd = new PosixDatagramFD(calls.posix)) {
            var address = new IPPort("127.0.0.1", 12345);
            assertEquals(0, fd.send(ByteBuffer.allocate(0), address));
            int original = fd.getFD();
            assertEquals(0, fd.getLocalAddress().getPort());
            fd.bind(address);
            assertEquals(original, fd.getFD()); assertEquals(1, calls.creates);
            assertEquals(12345, fd.getLocalAddress().getPort());
        }
        assertTrue(calls.open.isEmpty());
    }

    @Test public void firstSendRefreshesPreviouslyObservedUnboundAddress() throws IOException {
        var calls = new NativeCalls();
        try (var fd = new PosixDatagramFD(calls.posix)) {
            var address = new IPPort("127.0.0.1", 12345);
            assertEquals(0, fd.send(ByteBuffer.allocate(0), address));
            assertEquals(0, fd.getLocalAddress().getPort());
            assertEquals(1, fd.send(ByteBuffer.wrap(new byte[]{7}), address));
            assertEquals(23456, fd.getLocalAddress().getPort());
        }
        assertTrue(calls.open.isEmpty());
    }

    @Test public void wouldBlockDoesNotConfuseByteCountWithSocketBinding() throws IOException {
        for (boolean autoBind : new boolean[]{false, true}) {
            var calls = new NativeCalls();
            calls.sendResult = 0; calls.autoBind = autoBind;
            try (var fd = new PosixDatagramFD(calls.posix)) {
                var address = new IPPort("127.0.0.1", 12345);
                var src = ByteBuffer.wrap(new byte[]{7});
                assertEquals(0, fd.send(src, address)); assertEquals(0, src.position());
                if (autoBind) {
                    assertNotNull(fd.receive(ByteBuffer.allocate(4)));
                    try { fd.bind(address); fail("kernel already bound the socket"); }
                    catch (IOException expected) { }
                } else {
                    try { fd.receive(ByteBuffer.allocate(4)); fail("socket remains unbound"); }
                    catch (IOException expected) { }
                    fd.bind(address);
                }
                assertEquals(1, calls.creates);
            }
            assertTrue(calls.open.isEmpty());
        }
    }

    @Test public void ordinaryBindAndClosedErrorsKeepExistingBehavior() throws IOException {
        var calls = new NativeCalls();
        var fd = new PosixDatagramFD(calls.posix);
        fd.bind(new IPPort("127.0.0.1", 12345));
        assertNotNull(fd.receive(ByteBuffer.allocate(4)));
        fd.close();
        try { fd.bind(new IPPort("127.0.0.1", 12345)); fail(); }
        catch (IOException e) { assertEquals("closed", e.getMessage()); }
        assertTrue(calls.open.isEmpty());
    }
}

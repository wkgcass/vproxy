package io.vproxy.poc.panama;

import io.vproxy.base.connection.ConnectableConnection;
import io.vproxy.base.util.OS;
import io.vproxy.base.util.Utils;
import io.vproxy.base.util.direct.DirectByteBuffer;
import io.vproxy.base.util.direct.DirectMemoryUtils;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.IPPort;
import io.vproxy.vfd.TapInfo;
import io.vproxy.vfd.UDSPath;
import io.vproxy.vfd.posix.AEFiredEvent;
import io.vproxy.vfd.posix.GeneralPosix;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class TestPanamaGeneralPosix {
    private static GeneralPosix posix;
    private static long ae;
    private static AEFiredEvent.Array fired;
    private static DirectByteBuffer eventFdBuf;
    private static DirectByteBuffer tmpbuf;

    private final List<Integer> deferClose = new ArrayList<>();

    @BeforeClass
    public static void beforeClass() throws Exception {
        Utils.loadDynamicLibrary("vfdposix");
        posix = new GeneralPosix();
        ae = posix.aeCreateEventLoop(4096, false);
        fired = new AEFiredEvent.Array(posix.aeGetFired(ae).reinterpret(4096 * AEFiredEvent.LAYOUT.byteSize()));
        eventFdBuf = DirectMemoryUtils.allocateDirectBuffer(8);
        tmpbuf = DirectMemoryUtils.allocateDirectBuffer(8);
    }

    @AfterClass
    public static void afterClass() {
        posix.aeDeleteEventLoop(ae);
        eventFdBuf.clean(false);
        tmpbuf.clean(false);
    }

    @After
    public void tearDown() throws Exception {
        for (var fd : deferClose) {
            posix.aeDeleteFileEvent(ae, fd);
            try {
                posix.close(fd);
            } catch (IOException ignore) {
            }
        }
        deferClose.clear();
    }

    @Test
    public void aeReadable() {
        assertEquals(1, posix.aeReadable());
    }

    @Test
    public void aeWritable() {
        assertEquals(2, posix.aeWritable());
    }

    @Test
    public void openPipe() throws Exception {
        var pipe = posix.openPipe();
        deferClose.add(pipe[0]);
        deferClose.add(pipe[1]);

        assertNotEquals(0, pipe[0]);
        assertNotEquals(0, pipe[1]);
        assertEquals(2, pipe.length);
    }

    @Test
    public void aeApiPoll() throws Exception {
        var begin = System.currentTimeMillis();
        var n = posix.aeApiPoll(ae, 500);
        assertEquals(0, n);
        var end = System.currentTimeMillis();
        assertTrue(end - begin >= 500);
    }

    @Test
    public void aeCreateFileEvent() throws Exception {
        var pipe = posix.openPipe();
        deferClose.add(pipe[0]);
        deferClose.add(pipe[1]);

        posix.aeCreateFileEvent(ae, pipe[0], posix.aeReadable());
        eventFdBuf.getMemorySegment().set(ValueLayout.JAVA_LONG, 0, 123L);
        posix.write(pipe[1], eventFdBuf.realBuffer(), 0, 8);

        var n = posix.aeApiPoll(ae, 100);
        assertEquals(1, n);
        assertEquals(pipe[0], fired.get(0).getFd());
        assertEquals(posix.aeReadable(), fired.get(0).getMask());
        eventFdBuf.limit(8).position(0);
        n = posix.read(pipe[0], eventFdBuf.realBuffer(), 0, 8);
        assertEquals(8, n);
        assertEquals(123L, eventFdBuf.getMemorySegment().get(ValueLayout.JAVA_LONG, 0));
    }

    @Test
    public void aeUpdateFileEvent() throws Exception {
        var pipe = posix.openPipe();
        deferClose.add(pipe[0]);
        deferClose.add(pipe[1]);

        posix.aeCreateFileEvent(ae, pipe[1], posix.aeWritable());

        for (int i = 0; i < 10; ++i) {
            var n = posix.aeApiPoll(ae, 100);
            assertEquals(1, n);
            assertEquals(pipe[1], fired.get(0).getFd());
            assertEquals(posix.aeWritable(), fired.get(0).getMask());
        }

        posix.aeUpdateFileEvent(ae, pipe[1], posix.aeReadable());
        for (int i = 0; i < 10; ++i) {
            var n = posix.aeApiPoll(ae, 0);
            assertEquals(0, n);
        }
    }

    @Test
    public void aeDeleteFileEvent() throws Exception {
        var pipe = posix.openPipe();
        deferClose.add(pipe[0]);
        deferClose.add(pipe[1]);

        posix.aeCreateFileEvent(ae, pipe[1], posix.aeWritable());

        for (int i = 0; i < 10; ++i) {
            var n = posix.aeApiPoll(ae, 100);
            assertEquals(1, n);
            assertEquals(pipe[1], fired.get(0).getFd());
            assertEquals(posix.aeWritable(), fired.get(0).getMask());
        }

        posix.aeDeleteFileEvent(ae, pipe[1]);
        for (int i = 0; i < 10; ++i) {
            var n = posix.aeApiPoll(ae, 0);
            assertEquals(0, n);
        }
    }

    @Test
    public void aeDeleteEventLoop() throws Exception {
        var ae = posix.aeCreateEventLoop(16, true);
        posix.aeDeleteEventLoop(ae);
    }

    @Test
    public void setBlocking() throws Exception {
        var fd = posix.createIPv4TcpFD();
        deferClose.add(fd);

        posix.setBlocking(fd, true);
        posix.setBlocking(fd, false);
    }

    @Test
    public void setSoLinger() throws Exception {
        var fd = posix.createIPv4TcpFD();
        deferClose.add(fd);

        posix.setSoLinger(fd, 123);
        posix.setSoLinger(fd, 0);
    }

    @Test
    public void setReusePort() throws Exception {
        var fd = posix.createIPv4TcpFD();
        deferClose.add(fd);

        posix.setReusePort(fd, true);
        posix.setReusePort(fd, false);
    }

    @Test
    public void setRcvBuf() throws Exception {
        var fd = posix.createIPv4TcpFD();
        deferClose.add(fd);

        posix.setRcvBuf(fd, 123);
    }

    @Test
    public void setTcpNoDelay() throws Exception {
        var fd = posix.createIPv4TcpFD();
        deferClose.add(fd);

        posix.setTcpNoDelay(fd, true);
        posix.setTcpNoDelay(fd, false);
    }

    @Test
    public void setBroadcast() throws Exception {
        var fd = posix.createIPv4UdpFD();
        deferClose.add(fd);

        posix.setBroadcast(fd, true);
        posix.setBroadcast(fd, false);
    }

    @Test
    public void setIpTransparent() throws Exception {
        var fd = posix.createIPv4TcpFD();
        deferClose.add(fd);

        if (OS.isLinux()) {
            posix.setIpTransparent(fd, true);
            posix.setIpTransparent(fd, false);
        } else {
            try {
                posix.setIpTransparent(fd, true);
                fail();
            } catch (IOException ignore) {
            }
        }
    }

    @Test
    public void close() throws Exception {
        var fd = posix.createIPv4TcpFD();
        posix.close(fd);
    }

    @Test
    public void createIPv4TcpFD() throws Exception {
        var fd = posix.createIPv4TcpFD();
        deferClose.add(fd);

        posix.bindIPv4(fd, IP.ipv4Bytes2Int(IP.from("127.0.0.1").getAddress()), 29999);
    }

    @Test
    public void createIPv6TcpFD() throws Exception {
        var fd = posix.createIPv6TcpFD();
        deferClose.add(fd);

        posix.bindIPv6(fd, "::1", 29999);
    }

    @Test
    public void createIPv4UdpFD() throws Exception {
        var fd = posix.createIPv4UdpFD();
        deferClose.add(fd);

        posix.bindIPv4(fd, IP.ipv4Bytes2Int(IP.from("127.0.0.1").getAddress()), 29999);
    }

    @Test
    public void createIPv6UdpFD() throws Exception {
        var fd = posix.createIPv6UdpFD();
        deferClose.add(fd);

        posix.bindIPv6(fd, "::1", 29999);
    }

    @Test
    public void createIPFDs() throws Exception {
        var fd = posix.createIPv4TcpFD();
        deferClose.add(fd);
        posix.bindIPv4(fd, IP.ipv4Bytes2Int(IP.from("127.0.0.1").getAddress()), 29999);
        fd = posix.createIPv6TcpFD();
        deferClose.add(fd);
        posix.bindIPv6(fd, "::1", 29999);
        fd = posix.createIPv4UdpFD();
        deferClose.add(fd);
        posix.bindIPv4(fd, IP.ipv4Bytes2Int(IP.from("127.0.0.1").getAddress()), 29999);
        fd = posix.createIPv6UdpFD();
        deferClose.add(fd);
        posix.bindIPv6(fd, "::1", 29999);
    }

    @Test
    public void createUnixDomainSocketFD() throws Exception {
        var fd = posix.createUnixDomainSocketFD();
        deferClose.add(fd);

        var sockFileName = Utils.homefile("test-createUnixDomainSocketFD.sock");
        try {
            //noinspection ResultOfMethodCallIgnored
            new File(sockFileName).delete();
        } catch (Exception ignore) {
        }
        posix.bindUnixDomainSocket(fd, sockFileName);
        assertTrue(new File(sockFileName).exists());
    }

    @Test
    public void accept() throws Exception {
        var fd = posix.createIPv4TcpFD();
        deferClose.add(fd);

        posix.setBlocking(fd, false);
        posix.bindIPv4(fd, IP.ipv4Bytes2Int(IP.from("127.0.0.1").getAddress()), 29999);
        var n = posix.accept(fd);
        assertEquals(0, n);

        var conn = ConnectableConnection.create(new IPPort("127.0.0.1:29999"));

        n = posix.accept(fd);
        assertNotEquals(0, n);
        deferClose.add(n);

        posix.finishConnect(n);
        assertTrue(conn.channel.finishConnect());

        var remote = posix.getIPv4Remote(n);
        assertEquals(IP.from("127.0.0.1"), remote.toIPPort().getAddress());
        var local = posix.getIPv4Local(n);
        assertEquals(new IPPort("127.0.0.1:29999"), local.toIPPort());

        posix.shutdownOutput(n);

        eventFdBuf.realBuffer().limit(8).position(0);
        n = conn.channel.read(eventFdBuf.realBuffer());
        assertEquals(-1, n);

        conn.close();
    }

    @Test
    public void connectIPv4() throws Exception {
        var fd = posix.createIPv4TcpFD();
        deferClose.add(fd);
        posix.bindIPv4(fd, IP.ipv4Bytes2Int(IP.from("127.0.0.1").getAddress()), 29999);

        fd = posix.createIPv4TcpFD();
        deferClose.add(fd);

        posix.connectIPv4(fd, IP.ipv4Bytes2Int(IP.from("127.0.0.1").getAddress()), 29999);
    }

    @Test
    public void connectIPv6() throws Exception {
        var fd = posix.createIPv6TcpFD();
        deferClose.add(fd);
        posix.bindIPv6(fd, "::1", 29999);

        fd = posix.createIPv6TcpFD();
        deferClose.add(fd);

        posix.connectIPv6(fd, "::1", 29999);
    }

    @Test
    public void connectUDS() throws Exception {
        var fd = posix.createUnixDomainSocketFD();
        deferClose.add(fd);

        var sockFileName = Utils.homefile("test-connectUDS.sock");
        try {
            //noinspection ResultOfMethodCallIgnored
            new File(sockFileName).delete();
        } catch (Exception ignore) {
        }
        posix.bindUnixDomainSocket(fd, sockFileName);

        fd = posix.createUnixDomainSocketFD();
        deferClose.add(fd);

        posix.connectUDS(fd, sockFileName);
    }

    @Test
    public void getIPv6LocalRemote() throws Exception {
        var fd = posix.createIPv6TcpFD();
        deferClose.add(fd);

        posix.setBlocking(fd, false);
        posix.bindIPv6(fd, "::1", 29999);
        var n = posix.accept(fd);
        assertEquals(0, n);

        var conn = ConnectableConnection.create(new IPPort("[::1]:29999"));

        n = posix.accept(fd);
        assertNotEquals(0, n);
        deferClose.add(n);

        var remote = posix.getIPv6Remote(n);
        assertEquals(IP.from("::1"), remote.toIPPort().getAddress());
        var local = posix.getIPv6Local(n);
        assertEquals(new IPPort("[::1]:29999"), local.toIPPort());

        conn.close();
    }

    @Test
    public void getUDSLocalRemote() throws Exception {
        var fd = posix.createUnixDomainSocketFD();
        deferClose.add(fd);

        posix.setBlocking(fd, false);
        var sockFileName = Utils.homefile("test-getUDSLocalRemote.sock");
        try {
            //noinspection ResultOfMethodCallIgnored
            new File(sockFileName).delete();
        } catch (Exception ignore) {
        }
        posix.bindUnixDomainSocket(fd, sockFileName);
        var n = posix.accept(fd);
        assertEquals(0, n);

        var conn = ConnectableConnection.create(new UDSPath(sockFileName));

        n = posix.accept(fd);
        assertNotEquals(0, n);
        deferClose.add(n);

        var remote = posix.getUDSRemote(n);
        assertEquals(new UDSPath(""), remote.toIPPort());
        var local = posix.getUDSLocal(n);
        assertEquals(new UDSPath(sockFileName), local.toIPPort());

        conn.close();
    }

    @Test
    public void sendToIPv4() throws Exception {
        var fd = posix.createIPv4UdpFD();
        deferClose.add(fd);

        var fd2 = posix.createIPv4UdpFD();
        deferClose.add(fd2);

        posix.bindIPv4(fd2, IP.ipv4Bytes2Int(IP.from("127.0.0.1").getAddress()), 29999);

        eventFdBuf.getMemorySegment().set(ValueLayout.JAVA_LONG, 0, 123L);
        var n = posix.sendtoIPv4(fd, eventFdBuf.realBuffer(), 0, 8,
            IP.ipv4Bytes2Int(IP.from("127.0.0.1").getAddress()), 29999);
        assertEquals(8, n);

        var res = posix.recvfromIPv4(fd2, tmpbuf.realBuffer(), 0, 8);
        var l = tmpbuf.getMemorySegment().get(ValueLayout.JAVA_LONG, 0);
        assertEquals(123L, l);
        assertEquals(8, res.len);
        assertEquals(IP.from("127.0.0.1"), res.address.toIPPort().getAddress());
        assertEquals(posix.getIPv4Local(fd).toIPPort().getPort(), res.address.toIPPort().getPort());
    }

    @Test
    public void sendToIPv6() throws Exception {
        var fd = posix.createIPv6UdpFD();
        deferClose.add(fd);

        var fd2 = posix.createIPv6UdpFD();
        deferClose.add(fd2);

        posix.bindIPv6(fd2, "::1", 29999);

        eventFdBuf.getMemorySegment().set(ValueLayout.JAVA_LONG, 0, 123L);
        var n = posix.sendtoIPv6(fd, eventFdBuf.realBuffer(), 0, 8,
            "::1", 29999);
        assertEquals(8, n);

        var res = posix.recvfromIPv6(fd2, tmpbuf.realBuffer(), 0, 8);
        var l = tmpbuf.getMemorySegment().get(ValueLayout.JAVA_LONG, 0);
        assertEquals(123L, l);
        assertEquals(8, res.len);
        assertEquals(IP.from("::1"), res.address.toIPPort().getAddress());
        assertEquals(posix.getIPv4Local(fd).toIPPort().getPort(), res.address.toIPPort().getPort());
    }

    @Test
    public void currentTimeMillis() {
        var now = System.currentTimeMillis();
        var now2 = posix.currentTimeMillis();
        assertTrue(Math.abs(now - now2) < 2);
    }

    @Test
    public void tapNonBlockingSupported() throws Exception {
        if (OS.isLinux()) {
            assertTrue(posix.tapNonBlockingSupported());
        } else if (OS.isMac()) {
            assertFalse(posix.tapNonBlockingSupported());
        } else {
            try {
                posix.tapNonBlockingSupported();
                fail();
            } catch (IOException e) {
                assertEquals("unsupported on current platform", e.getMessage());
            }
        }
    }

    @Test
    public void tunNonBlockingSupported() throws Exception {
        if (OS.isLinux()) {
            assertTrue(posix.tunNonBlockingSupported());
        } else if (OS.isMac()) {
            assertTrue(posix.tunNonBlockingSupported());
        } else {
            try {
                posix.tunNonBlockingSupported();
                fail();
            } catch (IOException e) {
                assertEquals("unsupported on current platform", e.getMessage());
            }
        }
    }

    @Test
    public void createTapFD() throws Exception {
        TapInfo info;
        try {
            info = posix.createTapFD("utun12", true);
        } catch (IOException e) {
            if ("Operation not permitted".equals(e.getMessage())) {
                if ("root".equals(System.getProperty("user.home"))) {
                    throw e;
                }
                System.out.println("createTapFD test skipped");
                return;
            }
            throw e;
        }
        assertEquals("utun12", info.dev);
        assertNotEquals(0, info.fd);
    }

    @Test
    public void setCoreAffinityForCurrentThread() throws Exception {
        if (OS.isLinux()) {
            posix.setCoreAffinityForCurrentThread(1);
        } else {
            try {
                posix.setCoreAffinityForCurrentThread(1);
                fail();
            } catch (IOException e) {
                assertEquals("unsupported on current platform", e.getMessage());
            }
        }
    }
}

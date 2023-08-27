package io.vproxy.vfd.posix;

import io.vproxy.base.util.thread.VProxyThread;
import io.vproxy.pni.Allocator;
import io.vproxy.pni.PNIString;
import io.vproxy.pni.array.IntArray;
import io.vproxy.vfd.TapInfo;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

public class GeneralPosix implements Posix {
    public GeneralPosix() {
    }

    @Override
    public int aeReadable() {
        return PosixNative.get().aeReadable(VProxyThread.current().getEnv());
    }

    @Override
    public int aeWritable() {
        return PosixNative.get().aeWritable(VProxyThread.current().getEnv());
    }

    @Override
    public int[] openPipe() throws IOException {
        try (var allocator = Allocator.ofPooled()) {
            var arr = new IntArray(allocator, 2);
            PosixNative.get().openPipe(VProxyThread.current().getEnv(), arr);
            return new int[]{arr.get(0), arr.get(1)};
        }
    }

    @Override
    public long aeCreateEventLoop(int setsize, boolean preferPoll) throws IOException {
        return PosixNative.get().aeCreateEventLoop(VProxyThread.current().getEnv(),
            setsize, 0, preferPoll);
    }

    @Override
    public MemorySegment aeGetFired(long ae) {
        return PosixNative.get().aeGetFired(VProxyThread.current().getEnv(),
            ae);
    }

    @Override
    public MemorySegment aeGetFiredExtra(long ae) {
        return PosixNative.get().aeGetFiredExtra(VProxyThread.current().getEnv(),
            ae);
    }

    @Override
    public long aeCreateEventLoop(int setsize, int epfd, boolean preferPoll) throws IOException {
        return PosixNative.get().aeCreateEventLoop(VProxyThread.current().getEnv(),
            setsize, epfd, preferPoll);
    }

    @Override
    public int aeApiPoll(long ae, long wait) throws IOException {
        return PosixNative.get().aeApiPoll(VProxyThread.current().getEnv(),
            ae, wait);
    }

    @Override
    public int aeApiPollNow(long ae) throws IOException {
        return PosixNative.get().aeApiPollNow(VProxyThread.current().getEnv(),
            ae);
    }

    @Override
    public int aeGetFiredExtraNum(long ae) {
        return PosixNative.get().aeGetFiredExtraNum(VProxyThread.current().getEnv(),
            ae);
    }

    @Override
    public void aeCreateFileEvent(long ae, int fd, int mask) {
        PosixNative.get().aeCreateFileEvent(VProxyThread.current().getEnv(),
            ae, fd, mask);
    }

    @Override
    public void aeUpdateFileEvent(long ae, int fd, int mask) {
        PosixNative.get().aeUpdateFileEvent(VProxyThread.current().getEnv(),
            ae, fd, mask);
    }

    @Override
    public void aeDeleteFileEvent(long ae, int fd) {
        PosixNative.get().aeDeleteFileEvent(VProxyThread.current().getEnv(),
            ae, fd);
    }

    @Override
    public void aeDeleteEventLoop(long ae) {
        PosixNative.get().aeDeleteEventLoop(VProxyThread.current().getEnv(), ae);
    }

    @Override
    public void setBlocking(int fd, boolean v) throws IOException {
        PosixNative.get().setBlocking(VProxyThread.current().getEnv(),
            fd, v);
    }

    @Override
    public void setSoLinger(int fd, int v) throws IOException {
        PosixNative.get().setSoLinger(VProxyThread.current().getEnv(),
            fd, v);
    }

    @Override
    public void setReusePort(int fd, boolean v) throws IOException {
        PosixNative.get().setReusePort(VProxyThread.current().getEnv(),
            fd, v);
    }

    @Override
    public void setRcvBuf(int fd, int buflen) throws IOException {
        PosixNative.get().setRcvBuf(VProxyThread.current().getEnv(),
            fd, buflen);
    }

    @Override
    public void setTcpNoDelay(int fd, boolean v) throws IOException {
        PosixNative.get().setTcpNoDelay(VProxyThread.current().getEnv(),
            fd, v);
    }

    @Override
    public void setBroadcast(int fd, boolean v) throws IOException {
        PosixNative.get().setBroadcast(VProxyThread.current().getEnv(),
            fd, v);
    }

    @Override
    public void setIpTransparent(int fd, boolean v) throws IOException {
        PosixNative.get().setIpTransparent(VProxyThread.current().getEnv(),
            fd, v);
    }

    @Override
    public void close(int fd) throws IOException {
        PosixNative.get().close(VProxyThread.current().getEnv(), fd);
    }

    @Override
    public int createIPv4TcpFD() throws IOException {
        return PosixNative.get().createIPv4TcpFD(VProxyThread.current().getEnv());
    }

    @Override
    public int createIPv6TcpFD() throws IOException {
        return PosixNative.get().createIPv6TcpFD(VProxyThread.current().getEnv());
    }

    @Override
    public int createIPv4UdpFD() throws IOException {
        return PosixNative.get().createIPv4UdpFD(VProxyThread.current().getEnv());
    }

    @Override
    public int createIPv6UdpFD() throws IOException {
        return PosixNative.get().createIPv6UdpFD(VProxyThread.current().getEnv());
    }

    @Override
    public int createUnixDomainSocketFD() throws IOException {
        return PosixNative.get().createUnixDomainSocketFD(VProxyThread.current().getEnv());
    }

    @Override
    public void bindIPv4(int fd, int addrHostOrder, int port) throws IOException {
        PosixNative.get().bindIPv4(VProxyThread.current().getEnv(),
            fd, addrHostOrder, port);
    }

    @Override
    public void bindIPv6(int fd, String fullAddr, int port) throws IOException {
        try (var allocator = Allocator.ofPooled()) {
            PosixNative.get().bindIPv6(VProxyThread.current().getEnv(),
                fd, new PNIString(allocator, fullAddr), port);
        }
    }

    @Override
    public void bindUnixDomainSocket(int fd, String path) throws IOException {
        try (var allocator = Allocator.ofPooled()) {
            PosixNative.get().bindUnixDomainSocket(VProxyThread.current().getEnv(),
                fd, new PNIString(allocator, path));
        }
    }

    @Override
    public int accept(int fd) throws IOException {
        return PosixNative.get().accept(VProxyThread.current().getEnv(), fd);
    }

    @Override
    public void connectIPv4(int fd, int addrHostOrder, int port) throws IOException {
        PosixNative.get().connectIPv4(VProxyThread.current().getEnv(),
            fd, addrHostOrder, port);
    }

    @Override
    public void connectIPv6(int fd, String fullAddr, int port) throws IOException {
        try (var allocator = Allocator.ofPooled()) {
            PosixNative.get().connectIPv6(VProxyThread.current().getEnv(),
                fd, new PNIString(allocator, fullAddr), port);
        }
    }

    @Override
    public void connectUDS(int fd, String sock) throws IOException {
        try (var allocator = Allocator.ofPooled()) {
            PosixNative.get().connectUDS(VProxyThread.current().getEnv(),
                fd, new PNIString(allocator, sock));
        }
    }

    @Override
    public void finishConnect(int fd) throws IOException {
        PosixNative.get().finishConnect(VProxyThread.current().getEnv(), fd);
    }

    @Override
    public void shutdownOutput(int fd) throws IOException {
        PosixNative.get().shutdownOutput(VProxyThread.current().getEnv(), fd);
    }

    @Override
    public VSocketAddress getIPv4Local(int fd) throws IOException {
        try (var allocator = Allocator.ofPooled()) {
            var o = PosixNative.get().getIPv4Local(VProxyThread.current().getEnv(), fd, allocator);
            if (o == null) return null;
            return new SocketAddressIPv4(o.getIp(), o.getPort());
        }
    }

    @Override
    public VSocketAddress getIPv6Local(int fd) throws IOException {
        try (var allocator = Allocator.ofPooled()) {
            var o = PosixNative.get().getIPv6Local(VProxyThread.current().getEnv(), fd, allocator);
            if (o == null) return null;
            return new SocketAddressIPv6(o.getIp(), o.getPort());
        }
    }

    @Override
    public VSocketAddress getIPv4Remote(int fd) throws IOException {
        try (var allocator = Allocator.ofPooled()) {
            var o = PosixNative.get().getIPv4Remote(VProxyThread.current().getEnv(), fd, allocator);
            if (o == null) return null;
            return new SocketAddressIPv4(o.getIp(), o.getPort());
        }
    }

    @Override
    public VSocketAddress getIPv6Remote(int fd) throws IOException {
        try (var allocator = Allocator.ofPooled()) {
            var o = PosixNative.get().getIPv6Remote(VProxyThread.current().getEnv(), fd, allocator);
            if (o == null) return null;
            return new SocketAddressIPv6(o.getIp(), o.getPort());
        }
    }

    @Override
    public VSocketAddress getUDSLocal(int fd) throws IOException {
        try (var allocator = Allocator.ofPooled()) {
            var o = PosixNative.get().getUDSLocal(VProxyThread.current().getEnv(), fd, allocator);
            if (o == null) return null;
            return new SocketAddressUDS(o.getPath());
        }
    }

    @Override
    public VSocketAddress getUDSRemote(int fd) throws IOException {
        try (var allocator = Allocator.ofPooled()) {
            var o = PosixNative.get().getUDSRemote(VProxyThread.current().getEnv(), fd, allocator);
            if (o == null) return null;
            return new SocketAddressUDS(o.getPath());
        }
    }

    @Override
    public int read(int fd, ByteBuffer directBuffer, int off, int len) throws IOException {
        return PosixNative.get().read(VProxyThread.current().getEnv(),
            fd, directBuffer, off, len);
    }

    @Override
    public int write(int fd, ByteBuffer directBuffer, int off, int len) throws IOException {
        return PosixNative.get().write(VProxyThread.current().getEnv(),
            fd, directBuffer, off, len);
    }

    @Override
    public int sendtoIPv4(int fd, ByteBuffer directBuffer, int off, int len, int addrHostOrder, int port) throws IOException {
        return PosixNative.get().sendtoIPv4(VProxyThread.current().getEnv(),
            fd, directBuffer, off, len, addrHostOrder, port);
    }

    @Override
    public int sendtoIPv6(int fd, ByteBuffer directBuffer, int off, int len, String fullAddr, int port) throws IOException {
        try (var allocator = Allocator.ofPooled()) {
            return PosixNative.get().sendtoIPv6(VProxyThread.current().getEnv(),
                fd, directBuffer, off, len, new PNIString(allocator, fullAddr), port);
        }
    }

    @Override
    public UDPRecvResult recvfromIPv4(int fd, ByteBuffer directBuffer, int off, int len) throws IOException {
        try (var allocator = Allocator.ofPooled()) {
            var o = PosixNative.get().recvfromIPv4(VProxyThread.current().getEnv(),
                fd, directBuffer, off, len, allocator);
            if (o == null) return null;
            return new UDPRecvResult(new SocketAddressIPv4(o.getAddr().getIp(), o.getAddr().getPort()), o.getLen());
        }
    }

    @Override
    public UDPRecvResult recvfromIPv6(int fd, ByteBuffer directBuffer, int off, int len) throws IOException {
        try (var allocator = Allocator.ofPooled()) {
            var o = PosixNative.get().recvfromIPv6(VProxyThread.current().getEnv(),
                fd, directBuffer, off, len, allocator);
            if (o == null) return null;
            return new UDPRecvResult(new SocketAddressIPv6(o.getAddr().getIp(), o.getAddr().getPort()), o.getLen());
        }
    }

    @Override
    public long currentTimeMillis() {
        return PosixNative.get().currentTimeMillis(VProxyThread.current().getEnv());
    }

    @Override
    public boolean tapNonBlockingSupported() throws IOException {
        return PosixNative.get().tapNonBlockingSupported(VProxyThread.current().getEnv());
    }

    @Override
    public boolean tunNonBlockingSupported() throws IOException {
        return PosixNative.get().tunNonBlockingSupported(VProxyThread.current().getEnv());
    }

    @Override
    public TapInfo createTapFD(String dev, boolean isTun) throws IOException {
        try (var allocator = Allocator.ofPooled()) {
            var o = PosixNative.get().createTapFD(VProxyThread.current().getEnv(),
                new PNIString(allocator, dev), isTun, allocator);
            if (o == null) return null;
            return new TapInfo(o.getDevName(), o.getFd());
        }
    }

    @Override
    public void setCoreAffinityForCurrentThread(long mask) throws IOException {
        PosixNative.get().setCoreAffinityForCurrentThread(VProxyThread.current().getEnv(), mask);
    }
}

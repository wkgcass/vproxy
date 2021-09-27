package io.vproxy.vfd.posix;

import io.vproxy.vfd.TapInfo;

import java.io.IOException;
import java.nio.ByteBuffer;

public class GeneralPosix implements Posix {
    GeneralPosix() {
    }

    @Override
    native public boolean pipeFDSupported();

    @Override
    native public boolean onlySelectNow();

    @Override
    native public int aeReadable();

    @Override
    native public int aeWritable();

    @Override
    native public int[] openPipe() throws IOException;

    @Override
    native public long aeCreateEventLoop(int setsize, boolean preferPoll) throws IOException;

    @Override
    public int aeApiPoll(long ae, long wait, int[] fdArray, int[] eventsArray) throws IOException {
        return aeApiPoll0(ae, wait, fdArray, eventsArray);
    }

    private static native int aeApiPoll0(long ae, long wait, int[] fdArray, int[] eventsArray) throws IOException;

    @Override
    public void aeCreateFileEvent(long ae, int fd, int mask) {
        aeCreateFileEvent0(ae, fd, mask);
    }

    private static native void aeCreateFileEvent0(long ae, int fd, int mask);

    @Override
    public void aeUpdateFileEvent(long ae, int fd, int mask) {
        aeUpdateFileEvent0(ae, fd, mask);
    }

    private static native void aeUpdateFileEvent0(long ae, int fd, int mask);

    @Override
    public void aeDeleteFileEvent(long ae, int fd) {
        aeDeleteFileEvent0(ae, fd);
    }

    private static native void aeDeleteFileEvent0(long ae, int fd);

    @Override
    native public void aeDeleteEventLoop(long ae);

    @Override
    native public void setBlocking(int fd, boolean v) throws IOException;

    @Override
    native public void setSoLinger(int fd, int v) throws IOException;

    @Override
    native public void setReusePort(int fd, boolean v) throws IOException;

    @Override
    native public void setRcvBuf(int fd, int buflen) throws IOException;

    @Override
    native public void setTcpNoDelay(int fd, boolean v) throws IOException;

    @Override
    native public void setBroadcast(int fd, boolean v) throws IOException;

    @Override
    native public void setIpTransparent(int fd, boolean v) throws IOException;

    @Override
    native public void close(int fd) throws IOException;

    @Override
    native public int createIPv4TcpFD() throws IOException;

    @Override
    native public int createIPv6TcpFD() throws IOException;

    @Override
    native public int createIPv4UdpFD() throws IOException;

    @Override
    native public int createIPv6UdpFD() throws IOException;

    @Override
    native public int createUnixDomainSocketFD() throws IOException;

    @Override
    native public void bindIPv4(int fd, int addrHostOrder, int port) throws IOException;

    @Override
    native public void bindIPv6(int fd, String fullAddr, int port) throws IOException;

    @Override
    native public void bindUnixDomainSocket(int fd, String path) throws IOException;

    @Override
    native public int accept(int fd) throws IOException;

    @Override
    native public void connectIPv4(int fd, int addrHostOrder, int port) throws IOException;

    @Override
    native public void connectIPv6(int fd, String fullAddr, int port) throws IOException;

    @Override
    native public void connectUDS(int fd, String sock) throws IOException;

    @Override
    native public void finishConnect(int fd) throws IOException;

    @Override
    native public void shutdownOutput(int fd) throws IOException;

    @Override
    native public VSocketAddress getIPv4Local(int fd) throws IOException;

    @Override
    native public VSocketAddress getIPv6Local(int fd) throws IOException;

    @Override
    native public VSocketAddress getIPv4Remote(int fd) throws IOException;

    @Override
    native public VSocketAddress getIPv6Remote(int fd) throws IOException;

    @Override
    native public VSocketAddress getUDSLocal(int fd) throws IOException;

    @Override
    native public VSocketAddress getUDSRemote(int fd) throws IOException;

    @Override
    native public int read(int fd, ByteBuffer directBuffer, int off, int len) throws IOException;

    @Override
    native public int write(int fd, ByteBuffer directBuffer, int off, int len) throws IOException;

    @Override
    native public int sendtoIPv4(int fd, ByteBuffer directBuffer, int off, int len, int addrHostOrder, int port) throws IOException;

    @Override
    native public int sendtoIPv6(int fd, ByteBuffer directBuffer, int off, int len, String fullAddr, int port) throws IOException;

    @Override
    native public UDPRecvResult recvfromIPv4(int fd, ByteBuffer directBuffer, int off, int len) throws IOException;

    @Override
    native public UDPRecvResult recvfromIPv6(int fd, ByteBuffer directBuffer, int off, int len) throws IOException;

    @Override
    native public long currentTimeMillis();

    @Override
    native public boolean tapNonBlockingSupported() throws IOException;

    @Override
    native public boolean tunNonBlockingSupported() throws IOException;

    @Override
    native public TapInfo createTapFD(String dev, boolean isTun) throws IOException;

    @Override
    native public void setCoreAffinityForCurrentThread(long mask) throws IOException;
}

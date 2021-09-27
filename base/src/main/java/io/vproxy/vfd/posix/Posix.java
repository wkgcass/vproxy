package vproxy.vfd.posix;

import vproxy.vfd.TapInfo;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface Posix {
    boolean pipeFDSupported();

    boolean onlySelectNow();

    int aeReadable();

    int aeWritable();

    int[] openPipe() throws IOException;

    long aeCreateEventLoop(int setsize, boolean preferPoll) throws IOException;

    int aeApiPoll(long ae, long wait, int[] fdArray, int[] eventsArray) throws IOException;

    void aeCreateFileEvent(long ae, int fd, int mask);

    void aeUpdateFileEvent(long ae, int fd, int mask);

    void aeDeleteFileEvent(long ae, int fd);

    void aeDeleteEventLoop(long ae);

    void setBlocking(int fd, boolean v) throws IOException;

    void setSoLinger(int fd, int v) throws IOException;

    void setReusePort(int fd, boolean v) throws IOException;

    void setRcvBuf(int fd, int buflen) throws IOException;

    void setTcpNoDelay(int fd, boolean v) throws IOException;

    void setBroadcast(int fd, boolean v) throws IOException;

    void setIpTransparent(int fd, boolean v) throws IOException;

    void close(int fd) throws IOException;

    int createIPv4TcpFD() throws IOException;

    int createIPv6TcpFD() throws IOException;

    int createIPv4UdpFD() throws IOException;

    int createIPv6UdpFD() throws IOException;

    int createUnixDomainSocketFD() throws IOException;

    void bindIPv4(int fd, int addrHostOrder, int port) throws IOException;

    void bindIPv6(int fd, String fullAddr, int port) throws IOException;

    void bindUnixDomainSocket(int fd, String path) throws IOException;

    int accept(int fd) throws IOException;

    void connectIPv4(int fd, int addrHostOrder, int port) throws IOException;

    void connectIPv6(int fd, String fullAddr, int port) throws IOException;

    void connectUDS(int fd, String sock) throws IOException;

    void finishConnect(int fd) throws IOException;

    void shutdownOutput(int fd) throws IOException;

    VSocketAddress getIPv4Local(int fd) throws IOException;

    VSocketAddress getIPv6Local(int fd) throws IOException;

    VSocketAddress getIPv4Remote(int fd) throws IOException;

    VSocketAddress getIPv6Remote(int fd) throws IOException;

    VSocketAddress getUDSLocal(int fd) throws IOException;

    VSocketAddress getUDSRemote(int fd) throws IOException;

    int read(int fd, ByteBuffer directBuffer, int off, int len) throws IOException;

    int write(int fd, ByteBuffer directBuffer, int off, int len) throws IOException;

    int sendtoIPv4(int fd, ByteBuffer directBuffer, int off, int len, int addrHostOrder, int port) throws IOException;

    int sendtoIPv6(int fd, ByteBuffer directBuffer, int off, int len, String fullAddr, int port) throws IOException;

    UDPRecvResult recvfromIPv4(int fd, ByteBuffer directBuffer, int off, int len) throws IOException;

    UDPRecvResult recvfromIPv6(int fd, ByteBuffer directBuffer, int off, int len) throws IOException;

    long currentTimeMillis();

    boolean tapNonBlockingSupported() throws IOException;

    boolean tunNonBlockingSupported() throws IOException;

    TapInfo createTapFD(String dev, boolean isTun) throws IOException;

    void setCoreAffinityForCurrentThread(long mask) throws IOException;
}

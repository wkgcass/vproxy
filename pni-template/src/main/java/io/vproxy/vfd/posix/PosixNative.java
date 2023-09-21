package io.vproxy.vfd.posix;

import io.vproxy.pni.annotation.*;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

@Struct(skip = true)
@AlwaysAligned
@Name("aeFiredEvent")
@Include("ae.h")
class PNIAEFiredEvent {
    int fd;
    int mask;
}

@SuppressWarnings("unused")
@Function
interface PNIPosixNative {
    @Trivial
    int aeReadable();

    @Trivial
    int aeWritable();

    @Trivial
    void openPipe(int[] fds) throws IOException;

    @Trivial
    long aeCreateEventLoop(int setsize, int epfd, boolean preferPoll) throws IOException;

    @Trivial
    MemorySegment aeGetFired(long ae);

    @Trivial
    MemorySegment aeGetFiredExtra(long ae);

    int aeApiPoll(long ae, long wait) throws IOException;

    @Trivial
    int aeApiPollNow(long ae) throws IOException;

    @Trivial
    int aeGetFiredExtraNum(long ae);

    @Trivial
    void aeCreateFileEvent(long ae, int fd, int mask);

    @Trivial
    void aeUpdateFileEvent(long ae, int fd, int mask);

    @Trivial
    void aeDeleteFileEvent(long ae, int fd);

    @Trivial
    void aeDeleteEventLoop(long ae);

    @Trivial
    void setBlocking(int fd, boolean v) throws IOException;

    @Trivial
    void setSoLinger(int fd, int v) throws IOException;

    @Trivial
    void setReusePort(int fd, boolean v) throws IOException;

    @Trivial
    void setRcvBuf(int fd, int buflen) throws IOException;

    @Trivial
    void setTcpNoDelay(int fd, boolean v) throws IOException;

    @Trivial
    void setBroadcast(int fd, boolean v) throws IOException;

    @Trivial
    void setIpTransparent(int fd, boolean v) throws IOException;

    @Trivial
    void close(int fd) throws IOException;

    @Trivial
    int createIPv4TcpFD() throws IOException;

    @Trivial
    int createIPv6TcpFD() throws IOException;

    @Trivial
    int createIPv4UdpFD() throws IOException;

    @Trivial
    int createIPv6UdpFD() throws IOException;

    @Trivial
    int createUnixDomainSocketFD() throws IOException;

    @Trivial
    void bindIPv4(int fd, int addrHostOrder, int port) throws IOException;

    @Trivial
    void bindIPv6(int fd, String fullAddr, int port) throws IOException;

    @Trivial
    void bindUnixDomainSocket(int fd, String path) throws IOException;

    @Trivial
    int accept(int fd) throws IOException;

    @Trivial
    void connectIPv4(int fd, int addrHostOrder, int port) throws IOException;

    @Trivial
    void connectIPv6(int fd, String fullAddr, int port) throws IOException;

    @Trivial
    void connectUDS(int fd, String sock) throws IOException;

    @Trivial
    void finishConnect(int fd) throws IOException;

    @Trivial
    void shutdownOutput(int fd) throws IOException;

    @Trivial
    PNISocketAddressIPv4ST getIPv4Local(int fd) throws IOException;

    @Trivial
    PNISocketAddressIPv6ST getIPv6Local(int fd) throws IOException;

    @Trivial
    PNISocketAddressIPv4ST getIPv4Remote(int fd) throws IOException;

    @Trivial
    PNISocketAddressIPv6ST getIPv6Remote(int fd) throws IOException;

    @Trivial
    PNISocketAddressUDSST getUDSLocal(int fd) throws IOException;

    @Trivial
    PNISocketAddressUDSST getUDSRemote(int fd) throws IOException;

    @Trivial
    int read(int fd, @Raw ByteBuffer directBuffer, int off, int len) throws IOException;

    @Trivial
    int write(int fd, @Raw ByteBuffer directBuffer, int off, int len) throws IOException;

    @Trivial
    int sendtoIPv4(int fd, @Raw ByteBuffer directBuffer, int off, int len, int addrHostOrder, int port) throws IOException;

    @Trivial
    int sendtoIPv6(int fd, @Raw ByteBuffer directBuffer, int off, int len, String fullAddr, int port) throws IOException;

    @Trivial
    PNIUDPRecvResultIPv4ST recvfromIPv4(int fd, @Raw ByteBuffer directBuffer, int off, int len) throws IOException;

    @Trivial
    PNIUDPRecvResultIPv6ST recvfromIPv6(int fd, @Raw ByteBuffer directBuffer, int off, int len) throws IOException;

    @Trivial
    long currentTimeMillis();

    @Trivial
    boolean tapNonBlockingSupported() throws IOException;

    @Trivial
    boolean tunNonBlockingSupported() throws IOException;

    @Trivial
    PNITapInfoST createTapFD(String dev, boolean isTun) throws IOException;

    @Trivial
    void setCoreAffinityForCurrentThread(long mask) throws IOException;
}

@SuppressWarnings("unused")
@Struct
@AlwaysAligned
@Name("SocketAddressIPv4_st")
class PNISocketAddressIPv4ST {
    @Unsigned int ip;
    @Unsigned short port;
}

@SuppressWarnings("unused")
@Struct
@AlwaysAligned
@Name("SocketAddressIPv6_st")
class PNISocketAddressIPv6ST {
    @Len(40) String ip;
    @Unsigned short port;
}

@SuppressWarnings("unused")
@Struct
@AlwaysAligned
@Name("SocketAddressUDS_st")
class PNISocketAddressUDSST {
    @Len(4096) String path;
}

@SuppressWarnings("unused")
@Struct
@AlwaysAligned
@Name("UDPRecvResultIPv4_st")
class PNIUDPRecvResultIPv4ST {
    PNISocketAddressIPv4ST addr;
    @Unsigned int len;
}

@SuppressWarnings("unused")
@Struct
@AlwaysAligned
@Name("UDPRecvResultIPv6_st")
class PNIUDPRecvResultIPv6ST {
    PNISocketAddressIPv6ST addr;
    @Unsigned int len;
}

@SuppressWarnings("unused")
@Struct
@AlwaysAligned
@Name("TapInfo_st")
class PNITapInfoST {
    @Len(16) String devName;
    int fd;
}

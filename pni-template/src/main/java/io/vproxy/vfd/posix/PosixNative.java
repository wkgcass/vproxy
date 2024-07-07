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
@Downcall
interface PNIPosixNative {
    @LinkerOption.Critical
    int aeReadable();

    @LinkerOption.Critical
    int aeWritable();

    @LinkerOption.Critical
    void openPipe(int[] fds) throws IOException;

    @LinkerOption.Critical
    long aeCreateEventLoop(int setsize, int epfd, boolean preferPoll) throws IOException;

    @LinkerOption.Critical
    MemorySegment aeGetFired(long ae);

    @LinkerOption.Critical
    MemorySegment aeGetFiredExtra(long ae);

    int aeApiPoll(long ae, long wait) throws IOException;

    @LinkerOption.Critical
    int aeApiPollNow(long ae) throws IOException;

    @LinkerOption.Critical
    int aeGetFiredExtraNum(long ae);

    @LinkerOption.Critical
    void aeCreateFileEvent(long ae, int fd, int mask);

    @LinkerOption.Critical
    void aeUpdateFileEvent(long ae, int fd, int mask);

    @LinkerOption.Critical
    void aeDeleteFileEvent(long ae, int fd);

    @LinkerOption.Critical
    void aeDeleteEventLoop(long ae);

    @LinkerOption.Critical
    void setBlocking(int fd, boolean v) throws IOException;

    @LinkerOption.Critical
    void setSoLinger(int fd, int v) throws IOException;

    @LinkerOption.Critical
    void setReusePort(int fd, boolean v) throws IOException;

    @LinkerOption.Critical
    void setRcvBuf(int fd, int buflen) throws IOException;

    @LinkerOption.Critical
    void setTcpNoDelay(int fd, boolean v) throws IOException;

    @LinkerOption.Critical
    void setBroadcast(int fd, boolean v) throws IOException;

    @LinkerOption.Critical
    void setIpTransparent(int fd, boolean v) throws IOException;

    @LinkerOption.Critical
    void close(int fd) throws IOException;

    @LinkerOption.Critical
    int createIPv4TcpFD() throws IOException;

    @LinkerOption.Critical
    int createIPv6TcpFD() throws IOException;

    @LinkerOption.Critical
    int createIPv4UdpFD() throws IOException;

    @LinkerOption.Critical
    int createIPv6UdpFD() throws IOException;

    @LinkerOption.Critical
    int createUnixDomainSocketFD() throws IOException;

    @LinkerOption.Critical
    void bindIPv4(int fd, int addrHostOrder, int port) throws IOException;

    @LinkerOption.Critical
    void bindIPv6(int fd, String fullAddr, int port) throws IOException;

    @LinkerOption.Critical
    void bindUnixDomainSocket(int fd, String path) throws IOException;

    @LinkerOption.Critical
    int accept(int fd) throws IOException;

    @LinkerOption.Critical
    void connectIPv4(int fd, int addrHostOrder, int port) throws IOException;

    @LinkerOption.Critical
    void connectIPv6(int fd, String fullAddr, int port) throws IOException;

    @LinkerOption.Critical
    void connectUDS(int fd, String sock) throws IOException;

    @LinkerOption.Critical
    void finishConnect(int fd) throws IOException;

    @LinkerOption.Critical
    void shutdownOutput(int fd) throws IOException;

    @LinkerOption.Critical
    PNISocketAddressIPv4ST getIPv4Local(int fd) throws IOException;

    @LinkerOption.Critical
    PNISocketAddressIPv6ST getIPv6Local(int fd) throws IOException;

    @LinkerOption.Critical
    PNISocketAddressIPv4ST getIPv4Remote(int fd) throws IOException;

    @LinkerOption.Critical
    PNISocketAddressIPv6ST getIPv6Remote(int fd) throws IOException;

    @LinkerOption.Critical
    PNISocketAddressUDSST getUDSLocal(int fd) throws IOException;

    @LinkerOption.Critical
    PNISocketAddressUDSST getUDSRemote(int fd) throws IOException;

    @LinkerOption.Critical
    int read(int fd, @Raw ByteBuffer directBuffer, int off, int len) throws IOException;

    int readBlocking(int fd, @Raw ByteBuffer directBuffer, int off, int len) throws IOException;

    @LinkerOption.Critical
    int write(int fd, @Raw ByteBuffer directBuffer, int off, int len) throws IOException;

    @LinkerOption.Critical
    int sendtoIPv4(int fd, @Raw ByteBuffer directBuffer, int off, int len, int addrHostOrder, int port) throws IOException;

    @LinkerOption.Critical
    int sendtoIPv6(int fd, @Raw ByteBuffer directBuffer, int off, int len, String fullAddr, int port) throws IOException;

    @LinkerOption.Critical
    PNIUDPRecvResultIPv4ST recvfromIPv4(int fd, @Raw ByteBuffer directBuffer, int off, int len) throws IOException;

    @LinkerOption.Critical
    PNIUDPRecvResultIPv6ST recvfromIPv6(int fd, @Raw ByteBuffer directBuffer, int off, int len) throws IOException;

    @LinkerOption.Critical
    long currentTimeMillis();

    @LinkerOption.Critical
    boolean tapNonBlockingSupported() throws IOException;

    @LinkerOption.Critical
    boolean tunNonBlockingSupported() throws IOException;

    @LinkerOption.Critical
    PNITapInfoST createTapFD(String dev, boolean isTun) throws IOException;

    @LinkerOption.Critical
    void setCoreAffinityForCurrentThread(long mask) throws IOException;
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

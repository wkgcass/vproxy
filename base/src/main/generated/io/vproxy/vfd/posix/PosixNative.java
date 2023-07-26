package io.vproxy.vfd.posix;

import io.vproxy.pni.*;
import io.vproxy.pni.array.*;
import java.lang.foreign.*;
import java.lang.invoke.*;
import java.nio.ByteBuffer;

public class PosixNative {
    private PosixNative() {
    }

    private static final PosixNative INSTANCE = new PosixNative();

    public static PosixNative get() {
        return INSTANCE;
    }

    private static final MethodHandle aeReadableMH = PanamaUtils.lookupPNIFunction(true, "Java_io_vproxy_vfd_posix_PosixNative_aeReadable");

    public int aeReadable(PNIEnv ENV) {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) aeReadableMH.invokeExact(ENV.MEMORY);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwLast();
        }
        return ENV.returnInt();
    }

    private static final MethodHandle aeWritableMH = PanamaUtils.lookupPNIFunction(true, "Java_io_vproxy_vfd_posix_PosixNative_aeWritable");

    public int aeWritable(PNIEnv ENV) {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) aeWritableMH.invokeExact(ENV.MEMORY);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwLast();
        }
        return ENV.returnInt();
    }

    private static final MethodHandle openPipeMH = PanamaUtils.lookupPNIFunction(true, "Java_io_vproxy_vfd_posix_PosixNative_openPipe", PNIBuf.class /* fds */);

    public void openPipe(PNIEnv ENV, IntArray fds) throws java.io.IOException {
        ENV.reset();
        try (var POOLED = Allocator.ofPooled()) {
            int ERR;
            try {
                ERR = (int) openPipeMH.invokeExact(ENV.MEMORY, PNIBuf.memoryOf(POOLED, fds));
            } catch (Throwable THROWABLE) {
                throw PanamaUtils.convertInvokeExactException(THROWABLE);
            }
            if (ERR != 0) {
                ENV.throwIf(java.io.IOException.class);
                ENV.throwLast();
            }
        }
    }

    private static final MethodHandle aeCreateEventLoopMH = PanamaUtils.lookupPNIFunction(true, "Java_io_vproxy_vfd_posix_PosixNative_aeCreateEventLoop", int.class /* setsize */, boolean.class /* preferPoll */);

    public long aeCreateEventLoop(PNIEnv ENV, int setsize, boolean preferPoll) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) aeCreateEventLoopMH.invokeExact(ENV.MEMORY, setsize, preferPoll);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
        return ENV.returnLong();
    }

    private static final MethodHandle aeApiPollMH = PanamaUtils.lookupPNIFunction(false, "Java_io_vproxy_vfd_posix_PosixNative_aeApiPoll", long.class /* ae */, long.class /* wait */, MemorySegment.class /* fdArray */, MemorySegment.class /* eventsArray */);

    public int aeApiPoll(PNIEnv ENV, long ae, long wait, MemorySegment fdArray, MemorySegment eventsArray) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) aeApiPollMH.invokeExact(ENV.MEMORY, ae, wait, (MemorySegment) (fdArray == null ? MemorySegment.NULL : fdArray), (MemorySegment) (eventsArray == null ? MemorySegment.NULL : eventsArray));
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
        return ENV.returnInt();
    }

    private static final MethodHandle aeApiPollNowMH = PanamaUtils.lookupPNIFunction(true, "Java_io_vproxy_vfd_posix_PosixNative_aeApiPollNow", long.class /* ae */, MemorySegment.class /* fdArray */, MemorySegment.class /* eventsArray */);

    public int aeApiPollNow(PNIEnv ENV, long ae, MemorySegment fdArray, MemorySegment eventsArray) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) aeApiPollNowMH.invokeExact(ENV.MEMORY, ae, (MemorySegment) (fdArray == null ? MemorySegment.NULL : fdArray), (MemorySegment) (eventsArray == null ? MemorySegment.NULL : eventsArray));
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
        return ENV.returnInt();
    }

    private static final MethodHandle aeCreateFileEventMH = PanamaUtils.lookupPNIFunction(true, "Java_io_vproxy_vfd_posix_PosixNative_aeCreateFileEvent", long.class /* ae */, int.class /* fd */, int.class /* mask */);

    public void aeCreateFileEvent(PNIEnv ENV, long ae, int fd, int mask) {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) aeCreateFileEventMH.invokeExact(ENV.MEMORY, ae, fd, mask);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwLast();
        }
    }

    private static final MethodHandle aeUpdateFileEventMH = PanamaUtils.lookupPNIFunction(true, "Java_io_vproxy_vfd_posix_PosixNative_aeUpdateFileEvent", long.class /* ae */, int.class /* fd */, int.class /* mask */);

    public void aeUpdateFileEvent(PNIEnv ENV, long ae, int fd, int mask) {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) aeUpdateFileEventMH.invokeExact(ENV.MEMORY, ae, fd, mask);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwLast();
        }
    }

    private static final MethodHandle aeDeleteFileEventMH = PanamaUtils.lookupPNIFunction(true, "Java_io_vproxy_vfd_posix_PosixNative_aeDeleteFileEvent", long.class /* ae */, int.class /* fd */);

    public void aeDeleteFileEvent(PNIEnv ENV, long ae, int fd) {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) aeDeleteFileEventMH.invokeExact(ENV.MEMORY, ae, fd);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwLast();
        }
    }

    private static final MethodHandle aeDeleteEventLoopMH = PanamaUtils.lookupPNIFunction(true, "Java_io_vproxy_vfd_posix_PosixNative_aeDeleteEventLoop", long.class /* ae */);

    public void aeDeleteEventLoop(PNIEnv ENV, long ae) {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) aeDeleteEventLoopMH.invokeExact(ENV.MEMORY, ae);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwLast();
        }
    }

    private static final MethodHandle setBlockingMH = PanamaUtils.lookupPNIFunction(true, "Java_io_vproxy_vfd_posix_PosixNative_setBlocking", int.class /* fd */, boolean.class /* v */);

    public void setBlocking(PNIEnv ENV, int fd, boolean v) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) setBlockingMH.invokeExact(ENV.MEMORY, fd, v);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
    }

    private static final MethodHandle setSoLingerMH = PanamaUtils.lookupPNIFunction(true, "Java_io_vproxy_vfd_posix_PosixNative_setSoLinger", int.class /* fd */, int.class /* v */);

    public void setSoLinger(PNIEnv ENV, int fd, int v) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) setSoLingerMH.invokeExact(ENV.MEMORY, fd, v);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
    }

    private static final MethodHandle setReusePortMH = PanamaUtils.lookupPNIFunction(true, "Java_io_vproxy_vfd_posix_PosixNative_setReusePort", int.class /* fd */, boolean.class /* v */);

    public void setReusePort(PNIEnv ENV, int fd, boolean v) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) setReusePortMH.invokeExact(ENV.MEMORY, fd, v);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
    }

    private static final MethodHandle setRcvBufMH = PanamaUtils.lookupPNIFunction(true, "Java_io_vproxy_vfd_posix_PosixNative_setRcvBuf", int.class /* fd */, int.class /* buflen */);

    public void setRcvBuf(PNIEnv ENV, int fd, int buflen) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) setRcvBufMH.invokeExact(ENV.MEMORY, fd, buflen);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
    }

    private static final MethodHandle setTcpNoDelayMH = PanamaUtils.lookupPNIFunction(true, "Java_io_vproxy_vfd_posix_PosixNative_setTcpNoDelay", int.class /* fd */, boolean.class /* v */);

    public void setTcpNoDelay(PNIEnv ENV, int fd, boolean v) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) setTcpNoDelayMH.invokeExact(ENV.MEMORY, fd, v);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
    }

    private static final MethodHandle setBroadcastMH = PanamaUtils.lookupPNIFunction(true, "Java_io_vproxy_vfd_posix_PosixNative_setBroadcast", int.class /* fd */, boolean.class /* v */);

    public void setBroadcast(PNIEnv ENV, int fd, boolean v) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) setBroadcastMH.invokeExact(ENV.MEMORY, fd, v);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
    }

    private static final MethodHandle setIpTransparentMH = PanamaUtils.lookupPNIFunction(true, "Java_io_vproxy_vfd_posix_PosixNative_setIpTransparent", int.class /* fd */, boolean.class /* v */);

    public void setIpTransparent(PNIEnv ENV, int fd, boolean v) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) setIpTransparentMH.invokeExact(ENV.MEMORY, fd, v);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
    }

    private static final MethodHandle closeMH = PanamaUtils.lookupPNIFunction(true, "Java_io_vproxy_vfd_posix_PosixNative_close", int.class /* fd */);

    public void close(PNIEnv ENV, int fd) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) closeMH.invokeExact(ENV.MEMORY, fd);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
    }

    private static final MethodHandle createIPv4TcpFDMH = PanamaUtils.lookupPNIFunction(true, "Java_io_vproxy_vfd_posix_PosixNative_createIPv4TcpFD");

    public int createIPv4TcpFD(PNIEnv ENV) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) createIPv4TcpFDMH.invokeExact(ENV.MEMORY);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
        return ENV.returnInt();
    }

    private static final MethodHandle createIPv6TcpFDMH = PanamaUtils.lookupPNIFunction(true, "Java_io_vproxy_vfd_posix_PosixNative_createIPv6TcpFD");

    public int createIPv6TcpFD(PNIEnv ENV) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) createIPv6TcpFDMH.invokeExact(ENV.MEMORY);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
        return ENV.returnInt();
    }

    private static final MethodHandle createIPv4UdpFDMH = PanamaUtils.lookupPNIFunction(true, "Java_io_vproxy_vfd_posix_PosixNative_createIPv4UdpFD");

    public int createIPv4UdpFD(PNIEnv ENV) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) createIPv4UdpFDMH.invokeExact(ENV.MEMORY);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
        return ENV.returnInt();
    }

    private static final MethodHandle createIPv6UdpFDMH = PanamaUtils.lookupPNIFunction(true, "Java_io_vproxy_vfd_posix_PosixNative_createIPv6UdpFD");

    public int createIPv6UdpFD(PNIEnv ENV) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) createIPv6UdpFDMH.invokeExact(ENV.MEMORY);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
        return ENV.returnInt();
    }

    private static final MethodHandle createUnixDomainSocketFDMH = PanamaUtils.lookupPNIFunction(true, "Java_io_vproxy_vfd_posix_PosixNative_createUnixDomainSocketFD");

    public int createUnixDomainSocketFD(PNIEnv ENV) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) createUnixDomainSocketFDMH.invokeExact(ENV.MEMORY);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
        return ENV.returnInt();
    }

    private static final MethodHandle bindIPv4MH = PanamaUtils.lookupPNIFunction(true, "Java_io_vproxy_vfd_posix_PosixNative_bindIPv4", int.class /* fd */, int.class /* addrHostOrder */, int.class /* port */);

    public void bindIPv4(PNIEnv ENV, int fd, int addrHostOrder, int port) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) bindIPv4MH.invokeExact(ENV.MEMORY, fd, addrHostOrder, port);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
    }

    private static final MethodHandle bindIPv6MH = PanamaUtils.lookupPNIFunction(true, "Java_io_vproxy_vfd_posix_PosixNative_bindIPv6", int.class /* fd */, String.class /* fullAddr */, int.class /* port */);

    public void bindIPv6(PNIEnv ENV, int fd, PNIString fullAddr, int port) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) bindIPv6MH.invokeExact(ENV.MEMORY, fd, (MemorySegment) (fullAddr == null ? MemorySegment.NULL : fullAddr.MEMORY), port);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
    }

    private static final MethodHandle bindUnixDomainSocketMH = PanamaUtils.lookupPNIFunction(true, "Java_io_vproxy_vfd_posix_PosixNative_bindUnixDomainSocket", int.class /* fd */, String.class /* path */);

    public void bindUnixDomainSocket(PNIEnv ENV, int fd, PNIString path) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) bindUnixDomainSocketMH.invokeExact(ENV.MEMORY, fd, (MemorySegment) (path == null ? MemorySegment.NULL : path.MEMORY));
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
    }

    private static final MethodHandle acceptMH = PanamaUtils.lookupPNIFunction(true, "Java_io_vproxy_vfd_posix_PosixNative_accept", int.class /* fd */);

    public int accept(PNIEnv ENV, int fd) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) acceptMH.invokeExact(ENV.MEMORY, fd);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
        return ENV.returnInt();
    }

    private static final MethodHandle connectIPv4MH = PanamaUtils.lookupPNIFunction(true, "Java_io_vproxy_vfd_posix_PosixNative_connectIPv4", int.class /* fd */, int.class /* addrHostOrder */, int.class /* port */);

    public void connectIPv4(PNIEnv ENV, int fd, int addrHostOrder, int port) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) connectIPv4MH.invokeExact(ENV.MEMORY, fd, addrHostOrder, port);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
    }

    private static final MethodHandle connectIPv6MH = PanamaUtils.lookupPNIFunction(true, "Java_io_vproxy_vfd_posix_PosixNative_connectIPv6", int.class /* fd */, String.class /* fullAddr */, int.class /* port */);

    public void connectIPv6(PNIEnv ENV, int fd, PNIString fullAddr, int port) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) connectIPv6MH.invokeExact(ENV.MEMORY, fd, (MemorySegment) (fullAddr == null ? MemorySegment.NULL : fullAddr.MEMORY), port);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
    }

    private static final MethodHandle connectUDSMH = PanamaUtils.lookupPNIFunction(true, "Java_io_vproxy_vfd_posix_PosixNative_connectUDS", int.class /* fd */, String.class /* sock */);

    public void connectUDS(PNIEnv ENV, int fd, PNIString sock) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) connectUDSMH.invokeExact(ENV.MEMORY, fd, (MemorySegment) (sock == null ? MemorySegment.NULL : sock.MEMORY));
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
    }

    private static final MethodHandle finishConnectMH = PanamaUtils.lookupPNIFunction(true, "Java_io_vproxy_vfd_posix_PosixNative_finishConnect", int.class /* fd */);

    public void finishConnect(PNIEnv ENV, int fd) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) finishConnectMH.invokeExact(ENV.MEMORY, fd);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
    }

    private static final MethodHandle shutdownOutputMH = PanamaUtils.lookupPNIFunction(true, "Java_io_vproxy_vfd_posix_PosixNative_shutdownOutput", int.class /* fd */);

    public void shutdownOutput(PNIEnv ENV, int fd) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) shutdownOutputMH.invokeExact(ENV.MEMORY, fd);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
    }

    private static final MethodHandle getIPv4LocalMH = PanamaUtils.lookupPNIFunction(true, "Java_io_vproxy_vfd_posix_PosixNative_getIPv4Local", int.class /* fd */, MemorySegment.class /* return */);

    public io.vproxy.vfd.posix.SocketAddressIPv4ST getIPv4Local(PNIEnv ENV, int fd, Allocator ALLOCATOR) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) getIPv4LocalMH.invokeExact(ENV.MEMORY, fd, ALLOCATOR.allocate(io.vproxy.vfd.posix.SocketAddressIPv4ST.LAYOUT.byteSize()));
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
        var RESULT = ENV.returnPointer();
        return RESULT == null ? null : new io.vproxy.vfd.posix.SocketAddressIPv4ST(RESULT);
    }

    private static final MethodHandle getIPv6LocalMH = PanamaUtils.lookupPNIFunction(true, "Java_io_vproxy_vfd_posix_PosixNative_getIPv6Local", int.class /* fd */, MemorySegment.class /* return */);

    public io.vproxy.vfd.posix.SocketAddressIPv6ST getIPv6Local(PNIEnv ENV, int fd, Allocator ALLOCATOR) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) getIPv6LocalMH.invokeExact(ENV.MEMORY, fd, ALLOCATOR.allocate(io.vproxy.vfd.posix.SocketAddressIPv6ST.LAYOUT.byteSize()));
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
        var RESULT = ENV.returnPointer();
        return RESULT == null ? null : new io.vproxy.vfd.posix.SocketAddressIPv6ST(RESULT);
    }

    private static final MethodHandle getIPv4RemoteMH = PanamaUtils.lookupPNIFunction(true, "Java_io_vproxy_vfd_posix_PosixNative_getIPv4Remote", int.class /* fd */, MemorySegment.class /* return */);

    public io.vproxy.vfd.posix.SocketAddressIPv4ST getIPv4Remote(PNIEnv ENV, int fd, Allocator ALLOCATOR) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) getIPv4RemoteMH.invokeExact(ENV.MEMORY, fd, ALLOCATOR.allocate(io.vproxy.vfd.posix.SocketAddressIPv4ST.LAYOUT.byteSize()));
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
        var RESULT = ENV.returnPointer();
        return RESULT == null ? null : new io.vproxy.vfd.posix.SocketAddressIPv4ST(RESULT);
    }

    private static final MethodHandle getIPv6RemoteMH = PanamaUtils.lookupPNIFunction(true, "Java_io_vproxy_vfd_posix_PosixNative_getIPv6Remote", int.class /* fd */, MemorySegment.class /* return */);

    public io.vproxy.vfd.posix.SocketAddressIPv6ST getIPv6Remote(PNIEnv ENV, int fd, Allocator ALLOCATOR) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) getIPv6RemoteMH.invokeExact(ENV.MEMORY, fd, ALLOCATOR.allocate(io.vproxy.vfd.posix.SocketAddressIPv6ST.LAYOUT.byteSize()));
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
        var RESULT = ENV.returnPointer();
        return RESULT == null ? null : new io.vproxy.vfd.posix.SocketAddressIPv6ST(RESULT);
    }

    private static final MethodHandle getUDSLocalMH = PanamaUtils.lookupPNIFunction(true, "Java_io_vproxy_vfd_posix_PosixNative_getUDSLocal", int.class /* fd */, MemorySegment.class /* return */);

    public io.vproxy.vfd.posix.SocketAddressUDSST getUDSLocal(PNIEnv ENV, int fd, Allocator ALLOCATOR) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) getUDSLocalMH.invokeExact(ENV.MEMORY, fd, ALLOCATOR.allocate(io.vproxy.vfd.posix.SocketAddressUDSST.LAYOUT.byteSize()));
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
        var RESULT = ENV.returnPointer();
        return RESULT == null ? null : new io.vproxy.vfd.posix.SocketAddressUDSST(RESULT);
    }

    private static final MethodHandle getUDSRemoteMH = PanamaUtils.lookupPNIFunction(true, "Java_io_vproxy_vfd_posix_PosixNative_getUDSRemote", int.class /* fd */, MemorySegment.class /* return */);

    public io.vproxy.vfd.posix.SocketAddressUDSST getUDSRemote(PNIEnv ENV, int fd, Allocator ALLOCATOR) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) getUDSRemoteMH.invokeExact(ENV.MEMORY, fd, ALLOCATOR.allocate(io.vproxy.vfd.posix.SocketAddressUDSST.LAYOUT.byteSize()));
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
        var RESULT = ENV.returnPointer();
        return RESULT == null ? null : new io.vproxy.vfd.posix.SocketAddressUDSST(RESULT);
    }

    private static final MethodHandle readMH = PanamaUtils.lookupPNIFunction(true, "Java_io_vproxy_vfd_posix_PosixNative_read", int.class /* fd */, ByteBuffer.class /* directBuffer */, int.class /* off */, int.class /* len */);

    public int read(PNIEnv ENV, int fd, ByteBuffer directBuffer, int off, int len) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) readMH.invokeExact(ENV.MEMORY, fd, PanamaUtils.format(directBuffer), off, len);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
        return ENV.returnInt();
    }

    private static final MethodHandle writeMH = PanamaUtils.lookupPNIFunction(true, "Java_io_vproxy_vfd_posix_PosixNative_write", int.class /* fd */, ByteBuffer.class /* directBuffer */, int.class /* off */, int.class /* len */);

    public int write(PNIEnv ENV, int fd, ByteBuffer directBuffer, int off, int len) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) writeMH.invokeExact(ENV.MEMORY, fd, PanamaUtils.format(directBuffer), off, len);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
        return ENV.returnInt();
    }

    private static final MethodHandle sendtoIPv4MH = PanamaUtils.lookupPNIFunction(true, "Java_io_vproxy_vfd_posix_PosixNative_sendtoIPv4", int.class /* fd */, ByteBuffer.class /* directBuffer */, int.class /* off */, int.class /* len */, int.class /* addrHostOrder */, int.class /* port */);

    public int sendtoIPv4(PNIEnv ENV, int fd, ByteBuffer directBuffer, int off, int len, int addrHostOrder, int port) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) sendtoIPv4MH.invokeExact(ENV.MEMORY, fd, PanamaUtils.format(directBuffer), off, len, addrHostOrder, port);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
        return ENV.returnInt();
    }

    private static final MethodHandle sendtoIPv6MH = PanamaUtils.lookupPNIFunction(true, "Java_io_vproxy_vfd_posix_PosixNative_sendtoIPv6", int.class /* fd */, ByteBuffer.class /* directBuffer */, int.class /* off */, int.class /* len */, String.class /* fullAddr */, int.class /* port */);

    public int sendtoIPv6(PNIEnv ENV, int fd, ByteBuffer directBuffer, int off, int len, PNIString fullAddr, int port) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) sendtoIPv6MH.invokeExact(ENV.MEMORY, fd, PanamaUtils.format(directBuffer), off, len, (MemorySegment) (fullAddr == null ? MemorySegment.NULL : fullAddr.MEMORY), port);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
        return ENV.returnInt();
    }

    private static final MethodHandle recvfromIPv4MH = PanamaUtils.lookupPNIFunction(true, "Java_io_vproxy_vfd_posix_PosixNative_recvfromIPv4", int.class /* fd */, ByteBuffer.class /* directBuffer */, int.class /* off */, int.class /* len */, MemorySegment.class /* return */);

    public io.vproxy.vfd.posix.UDPRecvResultIPv4ST recvfromIPv4(PNIEnv ENV, int fd, ByteBuffer directBuffer, int off, int len, Allocator ALLOCATOR) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) recvfromIPv4MH.invokeExact(ENV.MEMORY, fd, PanamaUtils.format(directBuffer), off, len, ALLOCATOR.allocate(io.vproxy.vfd.posix.UDPRecvResultIPv4ST.LAYOUT.byteSize()));
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
        var RESULT = ENV.returnPointer();
        return RESULT == null ? null : new io.vproxy.vfd.posix.UDPRecvResultIPv4ST(RESULT);
    }

    private static final MethodHandle recvfromIPv6MH = PanamaUtils.lookupPNIFunction(true, "Java_io_vproxy_vfd_posix_PosixNative_recvfromIPv6", int.class /* fd */, ByteBuffer.class /* directBuffer */, int.class /* off */, int.class /* len */, MemorySegment.class /* return */);

    public io.vproxy.vfd.posix.UDPRecvResultIPv6ST recvfromIPv6(PNIEnv ENV, int fd, ByteBuffer directBuffer, int off, int len, Allocator ALLOCATOR) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) recvfromIPv6MH.invokeExact(ENV.MEMORY, fd, PanamaUtils.format(directBuffer), off, len, ALLOCATOR.allocate(io.vproxy.vfd.posix.UDPRecvResultIPv6ST.LAYOUT.byteSize()));
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
        var RESULT = ENV.returnPointer();
        return RESULT == null ? null : new io.vproxy.vfd.posix.UDPRecvResultIPv6ST(RESULT);
    }

    private static final MethodHandle currentTimeMillisMH = PanamaUtils.lookupPNIFunction(true, "Java_io_vproxy_vfd_posix_PosixNative_currentTimeMillis");

    public long currentTimeMillis(PNIEnv ENV) {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) currentTimeMillisMH.invokeExact(ENV.MEMORY);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwLast();
        }
        return ENV.returnLong();
    }

    private static final MethodHandle tapNonBlockingSupportedMH = PanamaUtils.lookupPNIFunction(true, "Java_io_vproxy_vfd_posix_PosixNative_tapNonBlockingSupported");

    public boolean tapNonBlockingSupported(PNIEnv ENV) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) tapNonBlockingSupportedMH.invokeExact(ENV.MEMORY);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
        return ENV.returnBool();
    }

    private static final MethodHandle tunNonBlockingSupportedMH = PanamaUtils.lookupPNIFunction(true, "Java_io_vproxy_vfd_posix_PosixNative_tunNonBlockingSupported");

    public boolean tunNonBlockingSupported(PNIEnv ENV) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) tunNonBlockingSupportedMH.invokeExact(ENV.MEMORY);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
        return ENV.returnBool();
    }

    private static final MethodHandle createTapFDMH = PanamaUtils.lookupPNIFunction(true, "Java_io_vproxy_vfd_posix_PosixNative_createTapFD", String.class /* dev */, boolean.class /* isTun */, MemorySegment.class /* return */);

    public io.vproxy.vfd.posix.TapInfoST createTapFD(PNIEnv ENV, PNIString dev, boolean isTun, Allocator ALLOCATOR) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) createTapFDMH.invokeExact(ENV.MEMORY, (MemorySegment) (dev == null ? MemorySegment.NULL : dev.MEMORY), isTun, ALLOCATOR.allocate(io.vproxy.vfd.posix.TapInfoST.LAYOUT.byteSize()));
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
        var RESULT = ENV.returnPointer();
        return RESULT == null ? null : new io.vproxy.vfd.posix.TapInfoST(RESULT);
    }

    private static final MethodHandle setCoreAffinityForCurrentThreadMH = PanamaUtils.lookupPNIFunction(true, "Java_io_vproxy_vfd_posix_PosixNative_setCoreAffinityForCurrentThread", long.class /* mask */);

    public void setCoreAffinityForCurrentThread(PNIEnv ENV, long mask) throws java.io.IOException {
        ENV.reset();
        int ERR;
        try {
            ERR = (int) setCoreAffinityForCurrentThreadMH.invokeExact(ENV.MEMORY, mask);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        if (ERR != 0) {
            ENV.throwIf(java.io.IOException.class);
            ENV.throwLast();
        }
    }
}
// metadata.generator-version: pni 21.0.0.8
// sha256:a2c3186511b9e2d06b922bba1a2f72cbf87317bb049ee4e388899b705a3d691a

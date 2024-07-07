package io.vproxy.vfd.windows;

import io.vproxy.pni.annotation.*;
import io.vproxy.vfd.posix.PNISocketAddressUnion;

import java.io.IOException;
import java.lang.foreign.MemorySegment;

@Downcall
interface PNIWindowsNative {
    boolean tapNonBlockingSupported() throws IOException;

    long createTapHandle(String dev) throws IOException;

    @LinkerOption.Critical
    @Impl(
        c = """
            BOOL status = CloseHandle(handle);
            if (!status) {
                return throwIOExceptionBasedOnErrno(env);
            }
            return 0;
            """
    )
    void closeHandle(@NativeType("HANDLE") PNIHANDLE handle) throws IOException;

    @LinkerOption.Critical
    @Impl(
        c = """
            int dummy;
            BOOL ok = AcceptEx(
                listenSocket,
                socketContext->socket,
                socketContext->buffers[0].buf,
                0,
                sizeof(SOCKADDR_IN)+16,
                sizeof(SOCKADDR_IN)+16,
                &dummy,
                &socketContext->overlapped
            );
            if (!ok) {
                if (WSAGetLastError() == WSA_IO_PENDING) {
                    env->return_ = 0;
                    return 0;
                }
                return throwIOExceptionBasedOnErrno(env);
            }
            env->return_ = 1;
            return 0;
            """
    )
    boolean acceptEx(@NativeType("SOCKET") PNISOCKET listenSocket, PNIVIOContext socketContext) throws IOException;

    @LinkerOption.Critical
    @Impl(
        c = """
            sockaddr* name;
            int nameSize;
            if (ctx->v4) {
                v_sockaddr_in v4name;
                j2cSockAddrIPv4(&v4name, addr->v4.ip, addr->v6.port);
                name = (sockaddr*)&v4name;
                nameSize = sizeof(v4name);
            } else {
                v_sockaddr_in6 v6name;
                j2cSockAddrIPv6(&v6name, addr->v6.ip, addr->v6.port);
                name = (sockaddr*)&v6name;
                nameSize = sizeof(v6name);
            }

            BOOL ok = GetConnectEx()(
                ctx->socket,
                name,
                nameSize,
                NULL,
                0,
                &ctx->overlapped
            );
            if (!ok) {
                if (WSAGetLastError() == WSA_IO_PENDING) {
                    env->return_ = 0;
                    return 0;
                }
                return throwIOExceptionBasedOnErrno(env);
            }
            env->return_ = 1;
            return 0;
            """
    )
    boolean tcpConnect(PNIVIOContext ctx, PNISocketAddressUnion addr) throws IOException;

    @LinkerOption.Critical
    @Impl(
        c = """
            int rlen = 0;
            int zeroflags = 0;
            int ret = WSARecv(
                (SOCKET)ctx->socket,
                ctx->buffers, ctx->bufferCount, &rlen, &zeroflags,
                overlapped, NULL
            );
            if (ret < 0) {
                if (WSAGetLastError() == WSA_IO_PENDING) {
                    env->return_ = -1;
                    return 0;
                }
                return throwIOExceptionBasedOnErrno(env);
            }
            env->return_ = rlen;
            return 0;
            """
    )
    int wsaRecv(PNIVIOContext ctx) throws IOException;

    @LinkerOption.Critical
    @Impl(
        c = """
            int rlen = 0;
            int zeroflags = 0;
            int ret = WSARecvFrom(
                ctx->socket,
                ctx->buffers, ctx->bufferCount, &rlen, &zeroflags,
                (sockaddr*)&ctx->addr, &ctx->addrLen,
                &ctx->overlapped, NULL
            );
            if (ret < 0) {
                if (WSAGetLastError() == WSA_IO_PENDING) {
                    env->return_ = -1;
                    return 0;
                }
            }
            env->return_ = rlen;
            return 0;
            """
    )
    int wsaRecvFrom(PNIVIOContext ctx) throws IOException;

    @LinkerOption.Critical
    @Impl(
        c = """
            int wlen = 0;
            int ret = WSASend(
                ctx->socket,
                ctx->buffers, ctx->bufferCount, &wlen, 0,
                overlapped, NULL
            );
            if (ret < 0) {
                if (WSAGetLastError() == WSA_IO_PENDING) {
                    env->return_ = -1;
                    return 0;
                }
                return throwIOExceptionBasedOnErrno(env);
            }
            env->return_ = wlen;
            return 0;
            """
    )
    int wsaSend(PNIVIOContext ctx) throws IOException;

    @LinkerOption.Critical
    @Impl(
        c = """
            WSABUF buf;
            buf.len = 0;
            int err = WSASendDisconnect(socket, &buf);
            if (err < 0) {
                return throwIOExceptionBasedOnErrno(env);
            }
            return 0;
            """
    )
    void wsaSendDisconnect(@NativeType("SOCKET") PNISOCKET socket) throws IOException;
}

@Struct(skip = true)
@Include("ws2def.h")
class PNIWSABUF {
    @Unsigned long len;
    MemorySegment buf;
}

@Struct(skip = true, typedef = false)
@Name("sockaddr_storage ")
@Include("ws2def.h")
class SockaddrStorage {
    short family;
    @Len(48) char[] pad1;
    long align;
    @Len(8) char[] pad2;
}

@Struct(skip = true)
@Name("VIOContext")
@Sizeof("VIOContext")
class PNIVIOContext {
    MemorySegment ud;
    PNIOverlapped overlapped;
    int ctxType;
    boolean v4;
    @Pointer @NativeType("SOCKET") PNISOCKET socket;
    int ioType;
    @Len(2) PNIWSABUF[] buffers;
    int bufferCount;
    SockaddrStorage addr;
    int addrLen;
}

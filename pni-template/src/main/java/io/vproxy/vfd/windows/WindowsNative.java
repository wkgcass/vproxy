package io.vproxy.vfd.windows;

import io.vproxy.pni.PNIRef;
import io.vproxy.pni.annotation.*;
import io.vproxy.vfd.posix.PNISocketAddressUnion;

import java.io.IOException;
import java.lang.foreign.MemorySegment;

@Downcall
interface PNIWindowsNative {
    boolean tapNonBlockingSupported() throws IOException;

    @NoAlloc
    PNISOCKET createTapHandle(String dev) throws IOException;

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
    void closeHandle(@NativeType("HANDLE") PNISOCKET handle) throws IOException;

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
                (LPDWORD)&dummy,
                (LPWSAOVERLAPPED)&socketContext->overlapped
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
            int ret = setsockopt(accepted, SOL_SOCKET, SO_UPDATE_ACCEPT_CONTEXT,
                                 (char*)&listenSocket, sizeof(SOCKET));
            if (ret < 0) {
                return throwIOExceptionBasedOnErrno(env);
            }
            return 0;
            """
    )
    void updateAcceptContext(@NativeType("SOCKET") PNISOCKET listenSocket, @NativeType("SOCKET") PNISOCKET accepted) throws IOException;

    @LinkerOption.Critical
    @Impl(
        c = """
            v_sockaddr* name;
            int nameSize;
            if (v4) {
                v_sockaddr_in v4name;
                j2cSockAddrIPv4(&v4name, 0, 0);
                v_bind(ctx->socket, (v_sockaddr*)&v4name, sizeof(v4name));

                j2cSockAddrIPv4(&v4name, addr->v4.ip, addr->v4.port);
                name = (v_sockaddr*)&v4name;
                nameSize = sizeof(v4name);
            } else {
                v_sockaddr_in6 v6name;
                j2cSockAddrIPv6(&v6name, "::", 0);
                v_bind(ctx->socket, (v_sockaddr*)&v6name, sizeof(v6name));

                j2cSockAddrIPv6(&v6name, addr->v6.ip, addr->v6.port);
                name = (v_sockaddr*)&v6name;
                nameSize = sizeof(v6name);
            }

            BOOL ok = GetConnectEx()(
                ctx->socket,
                name,
                nameSize,
                NULL,
                0,
                NULL,
                (LPOVERLAPPED)&ctx->overlapped
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
    boolean tcpConnect(PNIVIOContext ctx, boolean v4, PNISocketAddressUnion addr) throws IOException;

    @LinkerOption.Critical
    @Impl(
        c = """
            int nRcv = 0;
            int zeroflags = 0;
            int ret = WSARecv(
                (SOCKET)ctx->socket,
                ctx->buffers, ctx->bufferCount,
                (LPDWORD)&nRcv, (LPDWORD)&zeroflags,
                (LPWSAOVERLAPPED)&ctx->overlapped, NULL
            );
            if (ret < 0) {
                if (WSAGetLastError() == WSA_IO_PENDING) {
                    env->return_ = -1;
                    return 0;
                }
                return throwIOExceptionBasedOnErrno(env);
            }
            env->return_ = nRcv;
            return 0;
            """
    )
    int wsaRecv(PNIVIOContext ctx) throws IOException;

    @LinkerOption.Critical
    @Impl(
        c = """
            int nRcv = 0;
            int zeroflags = 0;
            int ret = WSARecvFrom(
                ctx->socket,
                ctx->buffers, ctx->bufferCount,
                (LPDWORD)&nRcv, (LPDWORD)&zeroflags,
                (v_sockaddr*)&ctx->addr, &ctx->addrLen,
                (LPWSAOVERLAPPED)&ctx->overlapped, NULL
            );
            if (ret < 0) {
                if (WSAGetLastError() == WSA_IO_PENDING) {
                    env->return_ = -1;
                    return 0;
                }
                return throwIOExceptionBasedOnErrno(env);
            }
            env->return_ = nRcv;
            return 0;
            """
    )
    int wsaRecvFrom(PNIVIOContext ctx) throws IOException;

    @LinkerOption.Critical
    @Impl(
        c = """
            int nSent = 0;
            int ret = WSASend(
                ctx->socket,
                ctx->buffers, ctx->bufferCount, (LPDWORD)&nSent, 0,
                (LPWSAOVERLAPPED)&ctx->overlapped, NULL
            );
            if (ret < 0) {
                if (WSAGetLastError() == WSA_IO_PENDING) {
                    env->return_ = -1;
                    return 0;
                }
                return throwIOExceptionBasedOnErrno(env);
            }
            env->return_ = nSent;
            return 0;
            """
    )
    int wsaSend(PNIVIOContext ctx) throws IOException;

    @LinkerOption.Critical
    @Impl(
        c = """
            v_sockaddr* name;
            int nameSize;
            if (v4) {
                v_sockaddr_in v4name;
                j2cSockAddrIPv4(&v4name, addr->v4.ip, addr->v4.port);
                name = (v_sockaddr*)&v4name;
                nameSize = sizeof(v4name);
            } else {
                v_sockaddr_in6 v6name;
                j2cSockAddrIPv6(&v6name, addr->v6.ip, addr->v6.port);
                name = (v_sockaddr*)&v6name;
                nameSize = sizeof(v6name);
            }

            int nSent = 0;
            int err = WSASendTo(
                ctx->socket,
                ctx->buffers, ctx->bufferCount, (LPDWORD)&nSent, 0,
                name, nameSize, &ctx->overlapped, NULL
            );
            if (err < 0) {
                return throwIOExceptionBasedOnErrno(env);
            }
            return 0;
            """
    )
    int wsaSendTo(PNIVIOContext ctx, boolean v4, PNISocketAddressUnion addr) throws IOException;

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

    @LinkerOption.Critical
    @Impl(
        c = """
            if (v4) {
                formatSocketAddressIPv4(sockaddr, &addr->v4);
            } else {
                if (formatSocketAddressIPv6(env, sockaddr, &addr->v6) == NULL) {
                    return -1;
                }
            }
            return 0;
            """
    )
    void convertAddress(MemorySegment sockaddr, boolean v4, PNISocketAddressUnion addr)
        throws IOException;
}

@Struct(skip = true)
@Include("ws2def.h")
class PNIWSABUF {
    @Unsigned
    long len;
    MemorySegment buf;
}

@Struct(skip = true, typedef = false)
@Name("sockaddr_storage")
@Include("ws2def.h")
class PNISockaddrStorage {
    short family;
    @Len(48)
    char[] pad1;
    long align;
    @Len(8)
    char[] pad2;
}

@Struct
class PNIVIOContext {
    // make it the same as msquic format
    MemorySegment ptr;
    PNIOverlapped overlapped;
    int ctxType;

    // ref
    PNIRef<?> ref;

    // for io operations
    @Pointer
    @NativeType("SOCKET")
    PNISOCKET socket;
    int ioType;
    @Len(2)
    PNIWSABUF[] buffers;
    int bufferCount;

    // for address manipulation
    PNISockaddrStorage addr;
    int addrLen;
}

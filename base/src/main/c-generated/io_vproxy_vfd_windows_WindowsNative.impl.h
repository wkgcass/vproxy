#include "io_vproxy_vfd_windows_WindowsNative.h"

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT int JNICALL Java_io_vproxy_vfd_windows_WindowsNative_closeHandle(PNIEnv_void * env, HANDLE handle) {
    BOOL status = CloseHandle(handle);
    if (!status) {
        return throwIOExceptionBasedOnErrno(env);
    }
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_windows_WindowsNative_cancelIo(PNIEnv_void * env, HANDLE handle) {
    BOOL ok = CancelIo(handle);
    if (!ok) {
        return throwIOExceptionBasedOnErrno(env);
    }
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_windows_WindowsNative_acceptEx(PNIEnv_bool * env, SOCKET listenSocket, VIOContext * socketContext) {
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
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_windows_WindowsNative_updateAcceptContext(PNIEnv_void * env, SOCKET listenSocket, SOCKET accepted) {
    int ret = setsockopt(accepted, SOL_SOCKET, SO_UPDATE_ACCEPT_CONTEXT,
                         (char*)&listenSocket, sizeof(SOCKET));
    if (ret < 0) {
        return throwIOExceptionBasedOnErrno(env);
    }
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_windows_WindowsNative_tcpConnect(PNIEnv_bool * env, VIOContext * ctx, uint8_t v4, SocketAddressUnion * addr) {
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
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_windows_WindowsNative_wsaRecv(PNIEnv_int * env, VIOContext * ctx) {
    int nRcv = 0;
    int zeroflags = 0;
    int ret = WSARecv(
        ctx->socket,
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
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_windows_WindowsNative_wsaRecvFrom(PNIEnv_int * env, VIOContext * ctx) {
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
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_windows_WindowsNative_readFile(PNIEnv_int * env, VIOContext * ctx) {
    int nRead = 0;
    int zeroflags = 0;
    BOOL ok = ReadFile(
        (HANDLE)ctx->socket,
        ctx->buffers[0].buf, ctx->buffers[0].len,
        (LPDWORD)&nRead,
        (LPOVERLAPPED)&ctx->overlapped
    );
    if (!ok) {
        if (GetLastError() == ERROR_IO_PENDING) {
            env->return_ = -1;
            return 0;
        }
        return throwIOExceptionBasedOnErrno(env);
    }
    env->return_ = nRead;
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_windows_WindowsNative_wsaSend(PNIEnv_int * env, VIOContext * ctx) {
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
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_windows_WindowsNative_wsaSendTo(PNIEnv_int * env, VIOContext * ctx, uint8_t v4, SocketAddressUnion * addr) {
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
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_windows_WindowsNative_wsaSendDisconnect(PNIEnv_void * env, SOCKET socket) {
    WSABUF buf;
    buf.len = 0;
    int err = WSASendDisconnect(socket, &buf);
    if (err < 0) {
        return throwIOExceptionBasedOnErrno(env);
    }
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_windows_WindowsNative_writeFile(PNIEnv_int * env, VIOContext * ctx) {
    int nWrote = 0;
    int ret = WriteFile(
        (HANDLE)ctx->socket,
        ctx->buffers[0].buf, ctx->buffers[0].len,
        (LPDWORD)&nWrote,
        (LPOVERLAPPED)&ctx->overlapped
    );
    if (ret < 0) {
        if (GetLastError() == ERROR_IO_PENDING) {
            env->return_ = -1;
            return 0;
        }
        return throwIOExceptionBasedOnErrno(env);
    }
    env->return_ = nWrote;
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_windows_WindowsNative_convertAddress(PNIEnv_void * env, void * sockaddr, uint8_t v4, SocketAddressUnion * addr) {
    if (v4) {
        formatSocketAddressIPv4(sockaddr, &addr->v4);
    } else {
        if (formatSocketAddressIPv6(env, sockaddr, &addr->v6) == NULL) {
            return -1;
        }
    }
    return 0;
}

#ifdef __cplusplus
}
#endif
// metadata.generator-version: pni 22.0.0.17
// sha256:de8dabb11987a65aae53ca0cc48dfa6284059ead90e2758f6d936655f82f311f

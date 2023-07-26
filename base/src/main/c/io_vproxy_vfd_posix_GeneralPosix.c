#ifdef __linux__
#define _GNU_SOURCE
#endif

#include "io_vproxy_vfd_posix_PosixNative.h"
#include "exception.h"
#include "vfd_posix.h"

#define LISTEN_BACKLOG 512

JNIEXPORT int JNICALL Java_io_vproxy_vfd_posix_PosixNative_aeReadable
  (PNIEnv_int* env) {
    env->return_ = AE_READABLE;
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_posix_PosixNative_aeWritable
  (PNIEnv_int* env) {
    env->return_ = AE_WRITABLE;
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_posix_PosixNative_openPipe
  (PNIEnv_void* env, PNIBuf* _fds) {
    int32_t* fds = _fds->buf;
        #ifdef __linux__
            int fd = eventfd(0, EFD_NONBLOCK);
            if (fd < 0) {
                return throwIOExceptionBasedOnErrno(env);
            }
            fds[0] = fd;
            fds[1] = fd;
            return 0;
        #else
            int pipes[2];
            int res = v_pipe(pipes);
            if (res < 0) {
                return throwIOExceptionBasedOnErrno(env);
            }
            for (int i = 0; i < 2; ++i) {
                int fd = pipes[i];
                int on = 1;
                res = v_ioctl(fd, V_FIONBIO, &on);
                if (res == -1) {
                    v_close(pipes[0]);
                    v_close(pipes[1]);
                    return throwIOExceptionBasedOnErrno(env);
                }
            }
            fds[0] = pipes[0];
            fds[1] = pipes[1];
            return 0;
        #endif
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_posix_PosixNative_aeCreateEventLoop
  (PNIEnv_long* env, int32_t setsize, uint8_t preferPoll) {
    aeEventLoop* ae = aeCreateEventLoop2(setsize, preferPoll);
    if (ae == NULL) {
        return throwIOException(env, "create ae failed");
    }
    env->return_ = (int64_t)ae;
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_posix_PosixNative_aeApiPoll
  (PNIEnv_int* env, int64_t aex, int64_t wait, void* _fdsArray, void* _eventsArray) {
    aeEventLoop* ae = (aeEventLoop*) aex;
    int32_t* fdsArray = _fdsArray;
    int32_t* eventsArray = _eventsArray;

    v_timeval tv;
    v_timeval* tvp = &tv;
    tvp->tv_sec = wait/1000;
    tvp->tv_usec = (wait % 1000)*1000;
    int numevents = aePoll(ae, tvp);

    for (int j = 0; j < numevents; j++) {
      aeFileEvent* fe = &(ae->events[ae->fired[j].fd]);
      int fd = ae->fired[j].fd;

      fdsArray[j] = fd;
      eventsArray[j] = fe->mask;
    }
    env->return_ = numevents;
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_posix_PosixNative_aeApiPollNow
  (PNIEnv_int* env, int64_t aex, void* _fdsArray, void* _eventsArray) {
    return Java_io_vproxy_vfd_posix_PosixNative_aeApiPoll(env, aex, 0, _fdsArray, _eventsArray);
}

inline static void io_vproxy_vfd_posix_GeneralPosix_aeCreateFileEvent0
  (int64_t aex, int32_t fd, int32_t mask) {
    aeEventLoop* ae = (aeEventLoop*) aex;
    aeCreateFileEvent(ae, fd, mask, NULL, NULL);
}
JNIEXPORT int JNICALL Java_io_vproxy_vfd_posix_PosixNative_aeCreateFileEvent
  (PNIEnv_void* env, int64_t aex, int32_t fd, int32_t mask) {
    io_vproxy_vfd_posix_GeneralPosix_aeCreateFileEvent0(aex, fd, mask);
    return 0;
}

inline static void io_vproxy_vfd_posix_GeneralPosix_aeUpdateFileEvent0
  (int64_t aex, int32_t fd, int32_t mask) {
    aeEventLoop* ae = (aeEventLoop*) aex;
    aeDeleteFileEvent(ae, fd, 0xffffffff);
    aeCreateFileEvent(ae, fd, mask, NULL, NULL);
}
JNIEXPORT int JNICALL Java_io_vproxy_vfd_posix_PosixNative_aeUpdateFileEvent
  (PNIEnv_void* env, int64_t aex, int32_t fd, int32_t mask) {
    io_vproxy_vfd_posix_GeneralPosix_aeUpdateFileEvent0(aex, fd, mask);
    return 0;
}

inline static void io_vproxy_vfd_posix_GeneralPosix_aeDeleteFileEvent0
  (int64_t aex, int32_t fd) {
    aeEventLoop* ae = (aeEventLoop*) aex;
    aeDeleteFileEvent(ae, fd, 0xffffffff);
}
JNIEXPORT int JNICALL Java_io_vproxy_vfd_posix_PosixNative_aeDeleteFileEvent
  (PNIEnv_void* env, int64_t aex, int32_t fd) {
    io_vproxy_vfd_posix_GeneralPosix_aeDeleteFileEvent0(aex, fd);
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_posix_PosixNative_aeDeleteEventLoop
  (PNIEnv_void* env, int64_t aex) {
    aeEventLoop* ae = (aeEventLoop*) aex;
    aeDeleteEventLoop(ae);
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_posix_PosixNative_setBlocking
  (PNIEnv_void* env, int32_t fd, uint8_t v) {
    int on;
    if (v) {
        on = 0;
    } else {
        on = 1;
    }
    int res = v_ioctl(fd, V_FIONBIO, &on);
    if (res == -1) {
        return throwIOExceptionBasedOnErrno(env);
    }
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_posix_PosixNative_setSoLinger
  (PNIEnv_void* env, int32_t fd, int32_t v) {
    v_linger sl;
    sl.l_onoff = 1;
    sl.l_linger = v;
    int res = v_setsockopt(fd, V_SOL_SOCKET, V_SO_LINGER, &sl, sizeof(v_linger));
    if (res < 0) {
        return throwIOExceptionBasedOnErrno(env);
    }
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_posix_PosixNative_setReusePort
  (PNIEnv_void* env, int32_t fd, uint8_t v) {
        int optval = v ? 1 : 0;
        int res = v_setsockopt(fd, V_SOL_SOCKET, V_SO_REUSEPORT, &optval, sizeof(int));
        if (res < 0) {
            return throwIOExceptionBasedOnErrno(env);
        }
        return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_posix_PosixNative_setRcvBuf
  (PNIEnv_void* env, int32_t fd, int32_t v) {
    int val = v;
    int res = v_setsockopt(fd, V_SOL_SOCKET, V_SO_RCVBUF, &val, sizeof(int));
    if (res < 0) {
        return throwIOExceptionBasedOnErrno(env);
    }
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_posix_PosixNative_setTcpNoDelay
  (PNIEnv_void* env, int32_t fd, uint8_t v) {
    int i = v ? 1 : 0;
    int res = v_setsockopt(fd, V_IPPROTO_TCP, V_TCP_NODELAY, &i, sizeof(int));
    if (res < 0) {
        return throwIOExceptionBasedOnErrno(env);
    }
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_posix_PosixNative_setBroadcast
  (PNIEnv_void* env, int32_t fd, uint8_t v) {
    int i = v ? 1 : 0;
    int res = v_setsockopt(fd, V_SOL_SOCKET, V_SO_BROADCAST, &i, sizeof(int));
    if (res < 0) {
        return throwIOExceptionBasedOnErrno(env);
    }
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_posix_PosixNative_setIpTransparent
  (PNIEnv_void* env, int32_t fd, uint8_t v) {
    int i = v ? 1 : 0;
    int res = v_setsockopt(fd, V_SOL_IP, V_IP_TRANSPARENT, &i, sizeof(int));
    if (res < 0) {
      return throwIOExceptionBasedOnErrno(env);
    }
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_posix_PosixNative_close
  (PNIEnv_void* env, int32_t fd) {
    int res = v_close(fd);
    if (res < 0) {
        return throwIOExceptionBasedOnErrno(env);
    }
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_posix_PosixNative_createIPv4TcpFD
  (PNIEnv_int* env) {
    int sockfd = v_socket(V_AF_INET, V_SOCK_STREAM, 0);
    if (sockfd < 0) {
        return throwIOExceptionBasedOnErrno(env);
    }
    env->return_ = sockfd;
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_posix_PosixNative_createIPv6TcpFD
  (PNIEnv_int* env) {
    int sockfd6 = v_socket(V_AF_INET6, V_SOCK_STREAM, 0);
    if (sockfd6 < 0) {
        return throwIOExceptionBasedOnErrno(env);
    }
    env->return_ = sockfd6;
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_posix_PosixNative_createIPv4UdpFD
  (PNIEnv_int* env) {
    int sockfd = v_socket(V_AF_INET, V_SOCK_DGRAM, 0);
    if (sockfd < 0) {
        return throwIOExceptionBasedOnErrno(env);
    }
    env->return_ = sockfd;
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_posix_PosixNative_createIPv6UdpFD
  (PNIEnv_int* env) {
    int sockfd6 = v_socket(V_AF_INET6, V_SOCK_DGRAM, 0);
    if (sockfd6 < 0) {
        return throwIOExceptionBasedOnErrno(env);
    }
    env->return_ = sockfd6;
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_posix_PosixNative_createUnixDomainSocketFD
  (PNIEnv_int* env) {
    int uds = v_socket(V_AF_UNIX, V_SOCK_STREAM, 0);
    if (uds < 0) {
      return throwIOExceptionBasedOnErrno(env);
    }
    env->return_ = uds;
    return 0;
}

void j2cSockAddrIPv4(v_sockaddr_in* name, int32_t addrHostOrder, uint16_t port) {
    v_bzero(name, sizeof(v_sockaddr_in));
    name->sin_family = V_AF_INET;
    name->sin_port = v_htons(port);
    name->sin_addr.s_addr = v_htonl(addrHostOrder);
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_posix_PosixNative_bindIPv4
  (PNIEnv_void* env, int32_t fd, int32_t addrHostOrder, int32_t port) {
    v_sockaddr_in name;
    j2cSockAddrIPv4(&name, addrHostOrder, port);
    int reuseport = 1;
    int res = v_setsockopt(fd, V_SOL_SOCKET, V_SO_REUSEADDR, &reuseport, sizeof(int));
    if (res < 0) {
        return throwIOExceptionBasedOnErrno(env);
    }
    res = v_bind(fd, (v_sockaddr*) &name, sizeof(v_sockaddr_in));
    if (res < 0) {
        return throwIOExceptionBasedOnErrno(env);
    }
    res = v_listen(fd, LISTEN_BACKLOG);
    if (res < 0) {
        if (errno != EOPNOTSUPP) { // maybe the fd is udp socket
            return throwIOExceptionBasedOnErrno(env);
        }
    }
    return 0;
}

int j2cSockAddrIPv6(v_sockaddr_in6* name, char* fullAddrCharArray, uint16_t port) {
    v_bzero(name, sizeof(v_sockaddr_in6));
    name->sin6_family = AF_INET6;
    name->sin6_port = htons(port);
    int res = v_inet_pton(V_AF_INET6, fullAddrCharArray, &(name->sin6_addr.s6_addr));
    return res;
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_posix_PosixNative_bindIPv6
  (PNIEnv_void* env, int32_t fd, char* fullAddr, int32_t port) {
    v_sockaddr_in6 name;
    int res = j2cSockAddrIPv6(&name, fullAddr, port);
    if (res < 0) {
        return throwIOExceptionBasedOnErrno(env);
    }
    int reuseport = 1;
    res = v_setsockopt(fd, V_SOL_SOCKET, V_SO_REUSEADDR, &reuseport, sizeof(int));
    if (res < 0) {
        return throwIOExceptionBasedOnErrno(env);
    }
    res = v_bind(fd, (v_sockaddr*) &name, sizeof(v_sockaddr_in6));
    if (res < 0) {
        return throwIOExceptionBasedOnErrno(env);
    }
    res = v_listen(fd, LISTEN_BACKLOG);
    if (res < 0) {
        if (errno != EOPNOTSUPP) { // maybe the fd is udp socket
            return throwIOExceptionBasedOnErrno(env);
        }
    }
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_posix_PosixNative_bindUnixDomainSocket
  (PNIEnv_void* env, int32_t fd, char* pathChars) {
    if (strlen(pathChars) >= UNIX_PATH_MAX) {
        return throwIOException(env, "path too long");
    }

    v_sockaddr_un uds_sockaddr;
    memset(&uds_sockaddr, 0, sizeof(uds_sockaddr));
    uds_sockaddr.sun_family = V_AF_UNIX;
    strcpy(uds_sockaddr.sun_path, pathChars);

    int res = v_bind(fd, (v_sockaddr*) &uds_sockaddr, sizeof(uds_sockaddr));
    if (res < 0) {
        return throwIOExceptionBasedOnErrno(env);
    }
    res = v_listen(fd, LISTEN_BACKLOG);
    if (res < 0) {
        return throwIOExceptionBasedOnErrno(env);
    }
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_posix_PosixNative_accept
  (PNIEnv_int* env, int32_t fd) {
    int ret = v_accept(fd, NULL, NULL);
    if (ret < 0) {
        if (errno == V_EAGAIN || errno == V_EWOULDBLOCK) {
            env->return_ = 0;
            return 0;
        }
        return throwIOExceptionBasedOnErrno(env);
    }
    env->return_ = ret;
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_posix_PosixNative_connectIPv4
  (PNIEnv_void* env, int32_t fd, int32_t addrHostOrder, int32_t port) {
    v_sockaddr_in name;
    j2cSockAddrIPv4(&name, addrHostOrder, port);
    int res = v_connect(fd, (v_sockaddr*) &name, sizeof(v_sockaddr_in));
    if (res < 0) {
        if (errno == V_EINPROGRESS) {
            return 0;
        }
        return throwIOExceptionBasedOnErrno(env);
    }
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_posix_PosixNative_connectIPv6
  (PNIEnv_void* env, int32_t fd, char* fullAddr, int32_t port) {
    v_sockaddr_in6 name;
    int res = j2cSockAddrIPv6(&name, fullAddr, port);
    if (res < 0) {
        return throwIOExceptionBasedOnErrno(env);
    }
    res = v_connect(fd, (v_sockaddr*) &name, sizeof(v_sockaddr_in6));
    if (res < 0) {
        if (errno == V_EINPROGRESS) {
            return 0;
        }
        return throwIOExceptionBasedOnErrno(env);
    }
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_posix_PosixNative_connectUDS
  (PNIEnv_void* env, int32_t fd, char* sockChars) {
    v_sockaddr_un addr;
    memset(&addr, 0, sizeof(v_sockaddr_un));
    addr.sun_family = V_AF_UNIX;
    strcpy(addr.sun_path, sockChars);
    int err = connect(fd, (struct sockaddr *) &addr, sizeof(v_sockaddr_un));
    if (err) {
        return throwIOExceptionBasedOnErrno(env);
    }
    return 0;
}

int32_t handleWriteIOOperationResult(void* env, int res) {
    if (res < 0) {
        if (errno == V_EAGAIN || errno == V_EWOULDBLOCK) {
            return 0;
        }
        return throwIOExceptionBasedOnErrno(env);
    }
    return res;
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_posix_PosixNative_finishConnect
  (PNIEnv_void* env, int32_t fd) {
    byte buf[0];
    int res = v_write(fd, 0, 0);
    return handleWriteIOOperationResult(env, res);
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_posix_PosixNative_shutdownOutput
  (PNIEnv_void* env, int32_t fd) {
    int res = v_shutdown(fd, V_SHUT_WR);
    if (res < 0) {
        return throwIOExceptionBasedOnErrno(env);
    }
    return 0;
}

void formatSocketAddressIPv4(v_sockaddr_in* addr, SocketAddressIPv4_st* st) {
    int32_t ip = v_ntohl(addr->sin_addr.s_addr);
    int32_t port = v_ntohs(addr->sin_port);
    st->ip = ip;
    st->port = port;
}

SocketAddressIPv6_st* formatSocketAddressIPv6(void* env, v_sockaddr_in6* addr, SocketAddressIPv6_st* st) {
    // build ip string
    char chars[40]; // 16bytes=32hex, 4hex in one group=7split, so 39 + 1(\0) is enough
    const char* res = v_inet_ntop(V_AF_INET6, &(addr->sin6_addr), chars, sizeof(chars));
    if (res == NULL) {
        throwIOExceptionBasedOnErrno(env);
        return NULL;
    }
    // retrieve the port
    int32_t port = v_ntohs(addr->sin6_port);

    // build result
    memcpy(st->ip, chars, 40);
    st->port = port;
    return st;
}

void formatSocketAddressUDS(v_sockaddr_un* addr, SocketAddressUDS_st* st) {
    char* path = addr->sun_path;
    strlcpy(st->path, path, 4096);
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_posix_PosixNative_getIPv4Local
  (PNIEnv_SocketAddressIPv4_st* env, int32_t fd, SocketAddressIPv4_st* st) {
    v_sockaddr_in name;
    unsigned int foo = sizeof(v_sockaddr_in);
    int res = v_getsockname(fd, (v_sockaddr*) &name, &foo);
    if (res < 0) {
        return throwIOExceptionBasedOnErrno(env);
    }
    env->return_ = st;
    formatSocketAddressIPv4(&name, env->return_);
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_posix_PosixNative_getIPv6Local
  (PNIEnv_SocketAddressIPv6_st* env, int32_t fd, SocketAddressIPv6_st* st) {
    v_sockaddr_in6 name;
    unsigned int foo = sizeof(v_sockaddr_in6);
    int res = v_getsockname(fd, (v_sockaddr*) &name, &foo);
    if (res < 0) {
        return throwIOExceptionBasedOnErrno(env);
    }
    env->return_ = st;
    if (formatSocketAddressIPv6(env, &name, env->return_) == NULL) {
        return -1;
    }
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_posix_PosixNative_getIPv4Remote
  (PNIEnv_SocketAddressIPv4_st* env, int32_t fd, SocketAddressIPv4_st* result) {
    v_sockaddr_in name;
    unsigned int foo = sizeof(v_sockaddr_in);
    int res = v_getpeername(fd, (v_sockaddr*) &name, &foo);
    if (res < 0) {
        return throwIOExceptionBasedOnErrno(env);
    }
    env->return_ = result;
    formatSocketAddressIPv4(&name, env->return_);
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_posix_PosixNative_getIPv6Remote
  (PNIEnv_SocketAddressIPv6_st* env, int32_t fd, SocketAddressIPv6_st* result) {
    v_sockaddr_in6 name;
    unsigned int foo = sizeof(v_sockaddr_in6);
    int res = v_getpeername(fd, (v_sockaddr*) &name, &foo);
    if (res < 0) {
        return throwIOExceptionBasedOnErrno(env);
    }
    env->return_ = result;
    if (formatSocketAddressIPv6(env, &name, env->return_) == NULL) {
        return -1;
    }
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_posix_PosixNative_getUDSLocal
  (PNIEnv_SocketAddressUDS_st* env, int32_t fd, SocketAddressUDS_st* result) {
    v_sockaddr_un name;
    memset(&name, 0, sizeof(name));
    unsigned int foo = sizeof(name);
    int res = v_getsockname(fd, (v_sockaddr*) &name, &foo);
    if (res < 0) {
        return throwIOExceptionBasedOnErrno(env);
    }
    env->return_ = result;
    formatSocketAddressUDS(&name, env->return_);
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_posix_PosixNative_getUDSRemote
  (PNIEnv_SocketAddressUDS_st* env, int32_t fd, SocketAddressUDS_st* result) {
    v_sockaddr_un name;
    memset(&name, 0, sizeof(name));
    unsigned int foo = sizeof(name);
    int res = v_getpeername(fd, (v_sockaddr*) &name, &foo);
    if (res < 0) {
        return throwIOExceptionBasedOnErrno(env);
    }
    env->return_ = result;
    formatSocketAddressUDS(&name, env->return_);
    return 0;
}

int32_t handleReadIOOperationResult(void* env, int res) {
    if (res < 0) {
        if (errno == V_EAGAIN || errno == V_EWOULDBLOCK) {
            return -2;
        }
        throwIOExceptionBasedOnErrno(env);
        return -3;
    } else if (res == 0) { // EOF
        return -1;
    }
    return res;
}

int32_t handleReadIOOperationResultFinal(void* env, int res) {
    int32_t n = handleReadIOOperationResult(env, res);
    if (n == -2) {
        n = 0;
    }
    return n;
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_posix_PosixNative_read
  (PNIEnv_int* env, int32_t fd, void* directBuffer, int32_t off, int32_t len) {
    if (len == 0) {
        env->return_ = 0;
        return 0;
    }
    byte* buf = (byte*) directBuffer;
    int res = v_read(fd, buf + off, len);
    env->return_ = handleReadIOOperationResultFinal(env, res);
    if (env->return_ == -3) {
        return -1;
    }
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_posix_PosixNative_write
  (PNIEnv_int* env, int32_t fd, void* directBuffer, int32_t off, int32_t len) {
    if (len == 0) {
        env->return_ = 0;
        return 0;
    }
    byte* buf = (byte*) directBuffer;
    int res = v_write(fd, buf + off, len);
    env->return_ = handleWriteIOOperationResult(env, res);
    if (env->return_ < 0) {
        return -1;
    }
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_posix_PosixNative_sendtoIPv4
  (PNIEnv_int* env, int32_t fd, void* directBuffer, int32_t off, int32_t len, int32_t addrHostOrder, int32_t port) {
    if (len == 0) {
        env->return_ = 0;
        return 0;
    }
    byte* buf = (byte*) directBuffer;
    v_sockaddr_in name;
    j2cSockAddrIPv4(&name, addrHostOrder, port);
    int res = v_sendto(fd, buf + off, len, 0, (v_sockaddr*) &name, sizeof(v_sockaddr_in));
    env->return_ = handleWriteIOOperationResult(env, res);
    if (env->return_ < 0) {
        return -1;
    }
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_posix_PosixNative_sendtoIPv6
  (PNIEnv_int* env, int32_t fd, void* directBuffer, int32_t off, int32_t len, char* fullAddr, int32_t port) {
    if (len == 0) {
        env->return_ = 0;
        return 0;
    }
    byte* buf = (byte*) directBuffer;
    v_sockaddr_in6 name;
    int res = j2cSockAddrIPv6(&name, fullAddr, port);
    if (res < 0) {
        return throwIOExceptionBasedOnErrno(env);
    }
    res = v_sendto(fd, buf + off, len, 0, (v_sockaddr*) &name, sizeof(v_sockaddr_in6));
    env->return_ = handleWriteIOOperationResult(env, res);
    if (env->return_ < 0) {
        return -1;
    }
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_posix_PosixNative_recvfromIPv4
  (PNIEnv_UDPRecvResultIPv4_st* env, int32_t fd, void* directBuffer, int32_t off, int32_t len, UDPRecvResultIPv4_st* result) {
    if (len == 0) {
        env->return_ = NULL;
        return 0;
    }
    byte* buf = (byte*) directBuffer;
    v_sockaddr_in name;
    unsigned int foo = sizeof(v_sockaddr_in);
    int res = v_recvfrom(fd, buf + off, len, 0, (v_sockaddr*) &name, &foo);
    int32_t retLen = handleReadIOOperationResult(env, res);
    if (res < 0) {
        if (retLen == -3) {
            return -1;
        }
        env->return_ = NULL;
        return 0;
    }
    env->return_ = result;
    formatSocketAddressIPv4(&name, &result->addr);
    result->len = retLen;
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_posix_PosixNative_recvfromIPv6
  (PNIEnv_UDPRecvResultIPv6_st* env, int32_t fd, void* directBuffer, int32_t off, int32_t len, UDPRecvResultIPv6_st* result) {
    if (len == 0) {
        env->return_ = NULL;
        return 0;
    }
    byte* buf = (byte*) directBuffer;
    v_sockaddr_in6 name;
    unsigned int foo = sizeof(v_sockaddr_in6);
    int res = v_recvfrom(fd, buf + off, len, 0, (v_sockaddr*) &name, &foo);
    int32_t retLen = handleReadIOOperationResult(env, res);
    if (res < 0) {
        if (retLen == -3) {
            return -1;
        }
        env->return_ = NULL;
        return 0;
    }
    env->return_ = result;
    SocketAddressIPv6_st* retAddr = formatSocketAddressIPv6(env, &name, &result->addr);
    if (retAddr == NULL) {
        return -1;
    }
    result->len = len;
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_posix_PosixNative_currentTimeMillis
  (PNIEnv_long* env) {
    v_timeval tv;
    v_gettimeofday(&tv, NULL);
    env->return_ = ((long)tv.tv_sec) * 1000 + tv.tv_usec / 1000;
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_posix_PosixNative_tapNonBlockingSupported
  (PNIEnv_bool* env) {
    #ifdef __linux__
      env->return_ = JNI_TRUE;
      return 0;
    #elif defined(__APPLE__)
      env->return_ = JNI_FALSE;
      return 0;
    #else
      return throwIOException(env, "unsupported on current platform");
    #endif
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_posix_PosixNative_tunNonBlockingSupported
  (PNIEnv_bool* env) {
    #ifdef __linux__
      env->return_ = JNI_TRUE;
      return 0;
    #elif defined(__APPLE__)
      env->return_ = JNI_TRUE;
      return 0;
    #else
      return throwIOException(env, "unsupported on current platform");
    #endif
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_posix_PosixNative_createTapFD
  (PNIEnv_TapInfo_st* env, char* devChars, uint8_t isTun, TapInfo_st* ret) {
    // the returned device name
    char devName[IFNAMSIZ];
    // fd for the tap char device
    int fd = 0;
    // tmpFd might be used
    int tmpFd = 0;
    // prepare the ifreq object
    struct ifreq ifr;
    memset(&ifr, 0, sizeof(ifr));

    #ifdef __linux__

      if ((fd = open("/dev/net/tun", O_RDWR)) < 0) {
          goto fail;
      }
      ifr.ifr_flags = 0;
      if (isTun) {
        ifr.ifr_flags |= IFF_TUN;
      } else {
        ifr.ifr_flags |= IFF_TAP;
      }
      ifr.ifr_flags |= IFF_NO_PI;
      strlcpy(ifr.ifr_name, devChars, IFNAMSIZ);

      if (ioctl(fd, TUNSETIFF, (void *) &ifr) < 0) {
          goto fail;
      }

      strlcpy(devName, ifr.ifr_name, IFNAMSIZ);

    // end ifdef __linux__
    #elif defined(__APPLE__)

    if (isTun) {
      // use macos utun
      if (!v_str_starts_with(devChars, "utun")) {
        return throwIOException(env, "tun devices must starts with `utun`");
      }
      int utun = atoi(devChars + 4); // 4 for "utun"
      if (utun < 0) {
        return throwIOException(env, "tun devices must be utun{n} where n >= 0");
      }

      fd = socket(PF_SYSTEM, SOCK_DGRAM, SYSPROTO_CONTROL);
      if (fd < 0) {
        goto fail;
      }
      struct ctl_info ctlInfo;
      strlcpy(ctlInfo.ctl_name, UTUN_CONTROL_NAME, sizeof(ctlInfo.ctl_name));
      struct sockaddr_ctl sc;
      if (ioctl(fd, CTLIOCGINFO, &ctlInfo) == -1) {
        goto fail;
      }
      sc.sc_id = ctlInfo.ctl_id;
      sc.sc_len = sizeof(sc);
      sc.sc_family = AF_SYSTEM;
      sc.ss_sysaddr = AF_SYS_CONTROL;
      sc.sc_unit = utun + 1;
      if (connect(fd, (struct sockaddr*) &sc, sizeof(sc)) < 0) {
        goto fail;
      }
      // set_nonblock
      fcntl(fd, F_SETFL, O_NONBLOCK);
    } else {
      // use tuntaposx kernel extension
      char tapFileName[16]; // usually /dev/tapX which is in minimum 5 + 4 chars and give it 16 would definitely be enough in normal cases
      int inputLen = strlen(devChars);
      if (inputLen > 10) { // 10 == 16 - 1 - 5
          return throwIOException(env, "input dev name is too long");
      }
      if (sprintf(tapFileName, "/dev/%s", devChars) < 0) {
          goto fail;
      }
      if ((fd = open(tapFileName, O_RDWR)) < 0) {
          goto fail;
      }
      // need to explicitly set the device to UP
      if ((tmpFd = v_socket(V_AF_INET, V_SOCK_DGRAM, 0)) < 0) {
          goto fail;
      }
      strlcpy(ifr.ifr_name, devChars, IFNAMSIZ);
      if (v_ioctl(tmpFd, V_SIOCGIFFLAGS, &ifr) < 0) {
          goto fail;
      }
      ifr.ifr_flags |= IFF_UP;
      if (v_ioctl(tmpFd, V_SIOCSIFFLAGS, &ifr) < 0) {
          goto fail;
      }
    }
      // copy the dev name to return str
      strlcpy(devName, devChars, IFNAMSIZ);

    // end defined(__APPLE__)
    #else
      return throwIOException(env, "unsupported on current platform");
    #endif

      env->return_ = ret;
      strlcpy(ret->devName, devName, IFNAMSIZ);
      ret->fd = fd;

      if (tmpFd > 0) {
          v_close(tmpFd);
      }
      return 0;
fail:
      if (fd > 0) {
          v_close(fd);
      }
      if (tmpFd > 0) {
          v_close(tmpFd);
      }
      return throwIOExceptionBasedOnErrno(env);
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_posix_PosixNative_setCoreAffinityForCurrentThread
  (PNIEnv_void* env, int64_t mask) {
#ifdef __linux__
    // get current thread
    pthread_t current = pthread_self();
    cpu_set_t cpuset;
    CPU_ZERO(&cpuset);
    for (int i = 0; i < 64; ++i) {
        int bit = (mask >> i) & 1;
        if (bit) {
            CPU_SET(i, &cpuset);
        }
    }
    int ret = pthread_setaffinity_np(current, sizeof(cpu_set_t), &cpuset);
    if (ret == 0) {
        return 0; // succeeded
    }
    return throwIOExceptionBasedOnErrno(env);
#else
    return throwIOException(env, "unsupported on current platform");
#endif
}

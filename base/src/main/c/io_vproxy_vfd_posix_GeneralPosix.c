#ifdef __linux__
#define _GNU_SOURCE
#endif

#include "io_vproxy_vfd_posix_GeneralPosix.h"
#include "vfd_posix.h"

#define LISTEN_BACKLOG 512

#ifdef DEBUG
#define SHOW_CRITICAL
#endif
#ifdef NO_SHOW_CRITICAL
#undef SHOW_CRITICAL
#endif

#ifndef USE_CRITICAL
#define USE_CRITICAL
#elif USE_CRITICAL == 0
#undef USE_CRITICAL
#endif

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_pipeFDSupported
  (JEnv* env) {
    env->return_z = JNI_TRUE;
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_onlySelectNow
  (JEnv* env) {
    env->return_z = JNI_FALSE;
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_aeReadable
  (JEnv* env) {
    env->return_i = AE_READABLE;
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_aeWritable
  (JEnv* env) {
    env->return_i = AE_WRITABLE;
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_openPipe
  (JEnv* env, uint32_t* fds) {
        #ifdef __linux__
            int fd = eventfd(0, EFD_NONBLOCK);
            if (fd < 0) {
                throwIOExceptionBasedOnErrno(env);
                return;
            }
            fds[0] = fd;
            fds[1] = fd;
            return;
        #else
            int pipes[2];
            int res = v_pipe(pipes);
            if (res < 0) {
                throwIOExceptionBasedOnErrno(env);
                return;
            }
            for (int i = 0; i < 2; ++i) {
                int fd = pipes[i];
                int on = 1;
                res = v_ioctl(fd, V_FIONBIO, &on);
                if (res == -1) {
                    v_close(pipes[0]);
                    v_close(pipes[1]);
                    throwIOExceptionBasedOnErrno(env);
                    return;
                }
            }
            fds[0] = pipes[0];
            fds[1] = pipes[1];
            return;
        #endif
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_aeCreateEventLoop
  (JEnv* env, uint32_t setsize, uint8_t preferPoll) {
    aeEventLoop* ae = aeCreateEventLoop2(setsize, preferPoll);
    if (ae == NULL) {
        throwIOException(env, "create ae failed");
    }
    env->return_j = (jlong)ae;
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_aeApiPoll
  (JEnv* env, void* aex, uint64_t wait, uint32_t* fdsArray, uint32_t* eventsArray) {
    aeEventLoop* ae = (aeEventLoop*) aex;
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
    env->return_i = numevents;
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_aeApiPollNow
  (JEnv* env, void* aex, uint32_t* fdsArray, uint32_t* eventsArray) {
    return Java_io_vproxy_vfd_posix_GeneralPosix_aeApiPoll(env, aex, 0, fdsArray, eventsArray);
}

inline static void io_vproxy_vfd_posix_GeneralPosix_aeCreateFileEvent0
  (void* aex, jint fd, jint mask) {
    aeEventLoop* ae = (aeEventLoop*) aex;
    aeCreateFileEvent(ae, fd, mask, NULL, NULL);
}
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_aeCreateFileEvent
  (JEnv* env, void* aex, jint fd, jint mask) {
    io_vproxy_vfd_posix_GeneralPosix_aeCreateFileEvent0(aex, fd, mask);
}

inline static void io_vproxy_vfd_posix_GeneralPosix_aeUpdateFileEvent0
  (void* aex, jint fd, jint mask) {
    aeEventLoop* ae = (aeEventLoop*) aex;
    aeDeleteFileEvent(ae, fd, 0xffffffff);
    aeCreateFileEvent(ae, fd, mask, NULL, NULL);
}
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_aeUpdateFileEvent
  (JEnv* env, void* aex, jint fd, jint mask) {
    io_vproxy_vfd_posix_GeneralPosix_aeUpdateFileEvent0(aex, fd, mask);
}

inline static void io_vproxy_vfd_posix_GeneralPosix_aeDeleteFileEvent0
  (void* aex, jint fd) {
    aeEventLoop* ae = (aeEventLoop*) aex;
    aeDeleteFileEvent(ae, fd, 0xffffffff);
}
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_aeDeleteFileEvent
  (JEnv* env, void* aex, jint fd) {
    io_vproxy_vfd_posix_GeneralPosix_aeDeleteFileEvent0(aex, fd);
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_aeDeleteEventLoop
  (JEnv* env, void* aex) {
    aeEventLoop* ae = (aeEventLoop*) aex;
    aeDeleteEventLoop(ae);
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_setBlocking
  (JEnv* env, uint32_t fd, uint8_t v) {
    int on;
    if (v) {
        on = 0;
    } else {
        on = 1;
    }
    int res = v_ioctl(fd, V_FIONBIO, &on);
    if (res == -1) {
        throwIOExceptionBasedOnErrno(env);
        return;
    }
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_setSoLinger
  (JEnv* env, uint32_t fd, int32_t v) {
    v_linger sl;
    sl.l_onoff = 1;
    sl.l_linger = v;
    int res = v_setsockopt(fd, V_SOL_SOCKET, V_SO_LINGER, &sl, sizeof(v_linger));
    if (res < 0) {
        throwIOExceptionBasedOnErrno(env);
    }
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_setReusePort
  (JEnv* env, uint32_t fd, uint8_t v) {
        int optval = v ? 1 : 0;
        int res = v_setsockopt(fd, V_SOL_SOCKET, V_SO_REUSEPORT, &optval, sizeof(int));
        if (res < 0) {
            throwIOExceptionBasedOnErrno(env);
        }
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_setRcvBuf
  (JEnv* env, uint32_t fd, uint32_t v) {
    int val = v;
    int res = v_setsockopt(fd, V_SOL_SOCKET, V_SO_RCVBUF, &val, sizeof(int));
    if (res < 0) {
        throwIOExceptionBasedOnErrno(env);
    }
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_setTcpNoDelay
  (JEnv* env, uint32_t fd, uint8_t v) {
    int i = v ? 1 : 0;
    int res = v_setsockopt(fd, V_IPPROTO_TCP, V_TCP_NODELAY, &i, sizeof(int));
    if (res < 0) {
        throwIOExceptionBasedOnErrno(env);
    }
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_setBroadcast
  (JEnv* env, uint32_t fd, uint8_t v) {
    int i = v ? 1 : 0;
    int res = v_setsockopt(fd, V_SOL_SOCKET, V_SO_BROADCAST, &i, sizeof(int));
    if (res < 0) {
        throwIOExceptionBasedOnErrno(env);
    }
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_setIpTransparent
  (JEnv* env, uint32_t fd, uint8_t v) {
    int i = v ? 1 : 0;
    int res = v_setsockopt(fd, V_SOL_IP, V_IP_TRANSPARENT, &i, sizeof(int));
    if (res < 0) {
      throwIOExceptionBasedOnErrno(env);
    }
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_close
  (JEnv* env, uint32_t fd) {
    int res = v_close(fd);
    if (res < 0) {
        throwIOExceptionBasedOnErrno(env);
    }
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_createIPv4TcpFD
  (JEnv* env) {
    int sockfd = v_socket(V_AF_INET, V_SOCK_STREAM, 0);
    if (sockfd < 0) {
        throwIOExceptionBasedOnErrno(env);
        return;
    }
    env->return_i = sockfd;
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_createIPv6TcpFD
  (JEnv* env) {
    int sockfd6 = v_socket(V_AF_INET6, V_SOCK_STREAM, 0);
    if (sockfd6 < 0) {
        throwIOExceptionBasedOnErrno(env);
        return;
    }
    env->return_i = sockfd6;
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_createIPv4UdpFD
  (JEnv* env) {
    int sockfd = v_socket(V_AF_INET, V_SOCK_DGRAM, 0);
    if (sockfd < 0) {
        throwIOExceptionBasedOnErrno(env);
        return;
    }
    env->return_i = sockfd;
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_createIPv6UdpFD
  (JEnv* env) {
    int sockfd6 = v_socket(V_AF_INET6, V_SOCK_DGRAM, 0);
    if (sockfd6 < 0) {
        throwIOExceptionBasedOnErrno(env);
        return;
    }
    env->return_i = sockfd6;
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_createUnixDomainSocketFD
  (JEnv* env) {
    int uds = v_socket(V_AF_UNIX, V_SOCK_STREAM, 0);
    if (uds < 0) {
      throwIOExceptionBasedOnErrno(env);
      return;
    }
    env->return_i = uds;
    return;
}

void j2cSockAddrIPv4(v_sockaddr_in* name, uint32_t addrHostOrder, uint16_t port) {
    v_bzero(name, sizeof(v_sockaddr_in));
    name->sin_family = V_AF_INET;
    name->sin_port = v_htons(port);
    name->sin_addr.s_addr = v_htonl(addrHostOrder);
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_bindIPv4
  (JEnv* env, uint32_t fd, uint32_t addrHostOrder, uint32_t port) {
    v_sockaddr_in name;
    j2cSockAddrIPv4(&name, addrHostOrder, port);
    int reuseport = 1;
    int res = v_setsockopt(fd, V_SOL_SOCKET, V_SO_REUSEADDR, &reuseport, sizeof(int));
    if (res < 0) {
        throwIOExceptionBasedOnErrno(env);
        return;
    }
    res = v_bind(fd, (v_sockaddr*) &name, sizeof(v_sockaddr_in));
    if (res < 0) {
        throwIOExceptionBasedOnErrno(env);
        return;
    }
    res = v_listen(fd, LISTEN_BACKLOG);
    if (res < 0) {
        if (errno != EOPNOTSUPP) { // maybe the fd is udp socket
            throwIOExceptionBasedOnErrno(env);
            return;
        }
    }
}

int j2cSockAddrIPv6(v_sockaddr_in6* name, char* fullAddrCharArray, uint16_t port) {
    v_bzero(name, sizeof(v_sockaddr_in6));
    name->sin6_family = AF_INET6;
    name->sin6_port = htons(port);
    int res = v_inet_pton(V_AF_INET6, fullAddrCharArray, &(name->sin6_addr.s6_addr));
    return res;
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_bindIPv6
  (JEnv* env, uint32_t fd, char* fullAddr, uint32_t port) {
    v_sockaddr_in6 name;
    int res = j2cSockAddrIPv6(&name, fullAddr, port);
    if (res < 0) {
        throwIOExceptionBasedOnErrno(env);
        return;
    }
    int reuseport = 1;
    res = v_setsockopt(fd, V_SOL_SOCKET, V_SO_REUSEADDR, &reuseport, sizeof(int));
    if (res < 0) {
        throwIOExceptionBasedOnErrno(env);
        return;
    }
    res = v_bind(fd, (v_sockaddr*) &name, sizeof(v_sockaddr_in6));
    if (res < 0) {
        throwIOExceptionBasedOnErrno(env);
        return;
    }
    res = v_listen(fd, LISTEN_BACKLOG);
    if (res < 0) {
        if (errno != EOPNOTSUPP) { // maybe the fd is udp socket
            throwIOExceptionBasedOnErrno(env);
            return;
        }
    }
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_bindUnixDomainSocket
  (JEnv* env, uint32_t fd, char* pathChars) {
    if (strlen(pathChars) >= UNIX_PATH_MAX) {
        throwIOException(env, "path too long");
        return;
    }

    v_sockaddr_un uds_sockaddr;
    memset(&uds_sockaddr, 0, sizeof(uds_sockaddr));
    uds_sockaddr.sun_family = V_AF_UNIX;
    strcpy(uds_sockaddr.sun_path, pathChars);

    int res = v_bind(fd, (v_sockaddr*) &uds_sockaddr, sizeof(uds_sockaddr));
    if (res < 0) {
        throwIOExceptionBasedOnErrno(env);
        return;
    }
    res = v_listen(fd, LISTEN_BACKLOG);
    if (res < 0) {
        throwIOExceptionBasedOnErrno(env);
        return;
    }
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_accept
  (JEnv* env, uint32_t fd) {
    int ret = v_accept(fd, NULL, NULL);
    if (ret < 0) {
        if (errno == V_EAGAIN || errno == V_EWOULDBLOCK) {
            env->return_i = 0;
            return;
        }
        throwIOExceptionBasedOnErrno(env);
        return;
    }
    env->return_i = ret;
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_connectIPv4
  (JEnv* env, uint32_t fd, uint32_t addrHostOrder, uint32_t port) {
    v_sockaddr_in name;
    j2cSockAddrIPv4(&name, addrHostOrder, port);
    int res = v_connect(fd, (v_sockaddr*) &name, sizeof(v_sockaddr_in));
    if (res < 0) {
        if (errno == V_EINPROGRESS) {
            return;
        }
        throwIOExceptionBasedOnErrno(env);
        return;
    }
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_connectIPv6
  (JEnv* env, uint32_t fd, char* fullAddr, uint32_t port) {
    v_sockaddr_in6 name;
    int res = j2cSockAddrIPv6(&name, fullAddr, port);
    if (res < 0) {
        throwIOExceptionBasedOnErrno(env);
        return;
    }
    res = v_connect(fd, (v_sockaddr*) &name, sizeof(v_sockaddr_in6));
    if (res < 0) {
        if (errno == V_EINPROGRESS) {
            return;
        }
        throwIOExceptionBasedOnErrno(env);
        return;
    }
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_connectUDS
  (JEnv* env, uint32_t fd, char* sockChars) {
    v_sockaddr_un addr;
    memset(&addr, 0, sizeof(v_sockaddr_un));
    addr.sun_family = V_AF_UNIX;
    strcpy(addr.sun_path, sockChars);
    int err = connect(fd, (struct sockaddr *) &addr, sizeof(v_sockaddr_un));
    if (err) {
        throwIOExceptionBasedOnErrno(env);
    }
}

jint handleWriteIOOperationResult(JEnv* env, int res) {
    if (res < 0) {
        if (errno == V_EAGAIN || errno == V_EWOULDBLOCK) {
            return 0;
        }
        throwIOExceptionBasedOnErrno(env);
        return 0;
    }
    return res;
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_finishConnect
  (JEnv* env, uint32_t fd) {
    byte buf[0];
    int res = v_write(fd, 0, 0);
    handleWriteIOOperationResult(env, res);
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_shutdownOutput
  (JEnv* env, uint32_t fd) {
    int res = v_shutdown(fd, V_SHUT_WR);
    if (res < 0) {
        throwIOExceptionBasedOnErrno(env);
        return;
    }
}

void formatSocketAddressIPv4(v_sockaddr_in* addr, SocketAddressIPv4_st* st) {
    jint ip = v_ntohl(addr->sin_addr.s_addr);
    jint port = v_ntohs(addr->sin_port);
    st->ip = ip;
    st->port = port;
}

SocketAddressIPv6_st* formatSocketAddressIPv6(JEnv* env, v_sockaddr_in6* addr, SocketAddressIPv6_st* st) {
    // build ip string
    char chars[40]; // 16bytes=32hex, 4hex in one group=7split, so 39 + 1(\0) is enough
    const char* res = v_inet_ntop(V_AF_INET6, &(addr->sin6_addr), chars, sizeof(chars));
    if (res == NULL) {
        throwIOExceptionBasedOnErrno(env);
        return NULL;
    }
    // retrieve the port
    jint port = v_ntohs(addr->sin6_port);

    // build result
    memcpy(st->ip, chars, 40);
    st->port = port;
    return st;
}

void formatSocketAddressUDS(v_sockaddr_un* addr, SocketAddressUDS_st* st) {
    char* path = addr->sun_path;
    strlcpy(st->path, path, 4096);
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_getIPv4Local
  (JEnv* env, uint32_t fd, SocketAddressIPv4_st* st) {
    v_sockaddr_in name;
    unsigned int foo = sizeof(v_sockaddr_in);
    int res = v_getsockname(fd, (v_sockaddr*) &name, &foo);
    if (res < 0) {
        throwIOExceptionBasedOnErrno(env);
        return;
    }
    env->return_p = st;
    formatSocketAddressIPv4(&name, env->return_p);
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_getIPv6Local
  (JEnv* env, uint32_t fd, SocketAddressIPv6_st* st) {
    v_sockaddr_in6 name;
    unsigned int foo = sizeof(v_sockaddr_in6);
    int res = v_getsockname(fd, (v_sockaddr*) &name, &foo);
    if (res < 0) {
        throwIOExceptionBasedOnErrno(env);
        return;
    }
    env->return_p = st;
    formatSocketAddressIPv6(env, &name, env->return_p);
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_getIPv4Remote
  (JEnv* env, uint32_t fd, SocketAddressIPv4_st* result) {
    v_sockaddr_in name;
    unsigned int foo = sizeof(v_sockaddr_in);
    int res = v_getpeername(fd, (v_sockaddr*) &name, &foo);
    if (res < 0) {
        throwIOExceptionBasedOnErrno(env);
        return;
    }
    env->return_p = result;
    formatSocketAddressIPv4(&name, env->return_p);
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_getIPv6Remote
  (JEnv* env, uint32_t fd, SocketAddressIPv6_st* result) {
    v_sockaddr_in6 name;
    unsigned int foo = sizeof(v_sockaddr_in6);
    int res = v_getpeername(fd, (v_sockaddr*) &name, &foo);
    if (res < 0) {
        throwIOExceptionBasedOnErrno(env);
        return;
    }
    env->return_p = result;
    formatSocketAddressIPv6(env, &name, env->return_p);
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_getUDSLocal
  (JEnv* env, uint32_t fd, SocketAddressUDS_st* result) {
    v_sockaddr_un name;
    memset(&name, 0, sizeof(name));
    unsigned int foo = sizeof(name);
    int res = v_getsockname(fd, (v_sockaddr*) &name, &foo);
    if (res < 0) {
        throwIOExceptionBasedOnErrno(env);
        return;
    }
    env->return_p = result;
    formatSocketAddressUDS(&name, env->return_p);
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_getUDSRemote
  (JEnv* env, uint32_t fd, SocketAddressUDS_st* result) {
    v_sockaddr_un name;
    memset(&name, 0, sizeof(name));
    unsigned int foo = sizeof(name);
    int res = v_getpeername(fd, (v_sockaddr*) &name, &foo);
    if (res < 0) {
        throwIOExceptionBasedOnErrno(env);
        return;
    }
    env->return_p = result;
    formatSocketAddressUDS(&name, env->return_p);
}

jint handleReadIOOperationResult(JEnv* env, int res) {
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

jint handleReadIOOperationResultFinal(JEnv* env, int res) {
    jint n = handleReadIOOperationResult(env, res);
    if (n == -2) {
        n = 0;
    }
    return n;
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_read
  (JEnv* env, uint32_t fd, void* directBuffer, uint32_t off, uint32_t len) {
    if (len == 0) {
        env->return_i = 0;
        return;
    }
    byte* buf = (byte*) directBuffer;
    int res = v_read(fd, buf + off, len);
    env->return_i = handleReadIOOperationResultFinal(env, res);
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_write
  (JEnv* env, uint32_t fd, void* directBuffer, uint32_t off, uint32_t len) {
    if (len == 0) {
        env->return_i = 0;
        return;
    }
    byte* buf = (byte*) directBuffer;
    int res = v_write(fd, buf + off, len);
    env->return_i = handleWriteIOOperationResult(env, res);
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_sendtoIPv4
  (JEnv* env, uint32_t fd, void* directBuffer, uint32_t off, uint32_t len, uint32_t addrHostOrder, uint32_t port) {
    if (len == 0) {
        env->return_i = 0;
        return;
    }
    byte* buf = (byte*) directBuffer;
    v_sockaddr_in name;
    j2cSockAddrIPv4(&name, addrHostOrder, port);
    int res = v_sendto(fd, buf + off, len, 0, (v_sockaddr*) &name, sizeof(v_sockaddr_in));
    env->return_i = handleWriteIOOperationResult(env, res);
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_sendtoIPv6
  (JEnv* env, uint32_t fd, void* directBuffer, uint32_t off, uint32_t len, char* fullAddr, uint32_t port) {
    if (len == 0) {
        env->return_i = 0;
        return;
    }
    byte* buf = (byte*) directBuffer;
    v_sockaddr_in6 name;
    int res = j2cSockAddrIPv6(&name, fullAddr, port);
    if (res < 0) {
        throwIOExceptionBasedOnErrno(env);
        return;
    }
    res = v_sendto(fd, buf + off, len, 0, (v_sockaddr*) &name, sizeof(v_sockaddr_in6));
    env->return_i = handleWriteIOOperationResult(env, res);
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_recvfromIPv4
  (JEnv* env, uint32_t fd, void* directBuffer, uint32_t off, uint32_t len, UDPRecvResultIPv4_st* result) {
    if (len == 0) {
        env->return_p = NULL;
        return;
    }
    byte* buf = (byte*) directBuffer;
    v_sockaddr_in name;
    unsigned int foo = sizeof(v_sockaddr_in);
    int res = v_recvfrom(fd, buf + off, len, 0, (v_sockaddr*) &name, &foo);
    jint retLen = handleReadIOOperationResult(env, res);
    if (res < 0) {
        env->return_p = NULL;
        return;
    }
    env->return_p = result;
    formatSocketAddressIPv4(&name, &result->addr);
    result->len = retLen;
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_recvfromIPv6
  (JEnv* env, jint fd, jlong directBuffer, jint off, jint len, UDPRecvResultIPv6_st* result) {
    if (len == 0) {
        env->return_p = NULL;
        return;
    }
    byte* buf = (byte*) directBuffer;
    v_sockaddr_in6 name;
    unsigned int foo = sizeof(v_sockaddr_in6);
    int res = v_recvfrom(fd, buf + off, len, 0, (v_sockaddr*) &name, &foo);
    jint retLen = handleReadIOOperationResult(env, res);
    if (res < 0) {
        env->return_p = NULL;
        return;
    }
    env->return_p = result;
    SocketAddressIPv6_st* retAddr = formatSocketAddressIPv6(env, &name, &result->addr);
    if (retAddr == NULL) {
        env->return_p = NULL;
        return;
    }
    result->len = len;
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_currentTimeMillis
  (JEnv* env) {
    v_timeval tv;
    v_gettimeofday(&tv, NULL);
    env->return_j = ((long)tv.tv_sec) * 1000 + tv.tv_usec / 1000;
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_tapNonBlockingSupported
  (JEnv* env) {
    #ifdef __linux__
      env->return_z = JNI_TRUE;
    #elif defined(__APPLE__)
      env->return_z = JNI_FALSE;
    #else
      throwIOException(env, "unsupported on current platform");
    #endif
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_tunNonBlockingSupported
  (JEnv* env) {
    #ifdef __linux__
      env->return_z = JNI_TRUE;
    #elif defined(__APPLE__)
      env->return_z = JNI_TRUE;
    #else
      throwIOException(env, "unsupported on current platform");
    #endif
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_createTapFD
  (JEnv* env, char* devChars, jboolean isTun, TapInfo_st* ret) {
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
        throwIOException(env, "tun devices must starts with `utun`");
        return;
      }
      int utun = atoi(devChars + 4); // 4 for "utun"
      if (utun < 0) {
        throwIOException(env, "tun devices must be utun{n} where n >= 0");
        return;
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
        throwIOException(env, "input dev name is too long");
        return;
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
      throwIOException(env, "unsupported on current platform");
      return;
    #endif

      env->return_p = ret;
      strlcpy(ret->devName, devName, IFNAMSIZ);
      ret->fd = fd;

      if (tmpFd > 0) {
          v_close(tmpFd);
      }
      return;
fail:
      if (fd > 0) {
          v_close(fd);
      }
      if (tmpFd > 0) {
          v_close(tmpFd);
      }
      throwIOExceptionBasedOnErrno(env);
      return;
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_setCoreAffinityForCurrentThread
  (JEnv* env, jlong mask) {
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
        return; // succeeded
    }
    throwIOExceptionBasedOnErrno(env);
#else
    throwIOException(env, "unsupported on current platform");
#endif
}

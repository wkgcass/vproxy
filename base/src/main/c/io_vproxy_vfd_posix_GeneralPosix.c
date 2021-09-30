#ifdef __linux__
#define _GNU_SOURCE
#endif

#include "io_vproxy_vfd_posix_GeneralPosix.h"
#include "vfd_posix.h"
#include "exception.h"

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

JNIEXPORT jboolean JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_pipeFDSupported
  (JNIEnv* env, jobject self) {
    #ifdef HAVE_FF_KQUEUE
        return JNI_FALSE;
    #else
        return JNI_TRUE;
    #endif
}

JNIEXPORT jboolean JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_onlySelectNow
  (JNIEnv* env, jobject self) {
    #ifdef HAVE_FF_KQUEUE
        return JNI_TRUE;
    #else
        return JNI_FALSE;
    #endif
}

JNIEXPORT jint JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_aeReadable
  (JNIEnv* env, jobject self) {
    return AE_READABLE;
}

JNIEXPORT jint JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_aeWritable
  (JNIEnv* env, jobject self) {
    return AE_WRITABLE;
}

JNIEXPORT jintArray JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_openPipe
  (JNIEnv* env, jobject self) {
    #ifdef HAVE_FF_KQUEUE
        return NULL;
    #else
        #ifdef __linux__
            int fd = eventfd(0, EFD_NONBLOCK);
            if (fd < 0) {
                throwIOExceptionBasedOnErrno(env);
                return NULL;
            }
            jintArray ret = (*env)->NewIntArray(env, 2);
            jint elems[] = { fd, fd };
            (*env)->SetIntArrayRegion(env, ret, 0, 2, elems);
            return ret;
        #else
            int pipes[2];
            int res = v_pipe(pipes);
            if (res < 0) {
                throwIOExceptionBasedOnErrno(env);
                return NULL;
            }
            for (int i = 0; i < 2; ++i) {
                int fd = pipes[i];
                int on = 1;
                res = v_ioctl(fd, V_FIONBIO, &on);
                if (res == -1) {
                    v_close(pipes[0]);
                    v_close(pipes[1]);
                    throwIOExceptionBasedOnErrno(env);
                    return NULL;
                }
            }
            jintArray ret = (*env)->NewIntArray(env, 2);
            jint elems[] = { pipes[0], pipes[1] };
            (*env)->SetIntArrayRegion(env, ret, 0, 2, elems);
            return ret;
        #endif
    #endif
}

JNIEXPORT jlong JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_aeCreateEventLoop
  (JNIEnv* env, jobject self, jint setsize, jboolean preferPoll) {
    aeEventLoop* ae = aeCreateEventLoop2(setsize, preferPoll);
    if (ae == NULL) {
        throwIOException(env, "create ae failed");
    }
    return (jlong)ae;
}

JNIEXPORT jint JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_aeApiPoll0
  (JNIEnv* env, jclass self, jlong aex, jlong wait, jintArray fdsArray, jintArray eventsArray) {
    aeEventLoop* ae = (aeEventLoop*) aex;
    v_timeval tv;
    v_timeval* tvp = &tv;
    tvp->tv_sec = wait/1000;
    tvp->tv_usec = (wait % 1000)*1000;
    int numevents = aePoll(ae, tvp);

    for (int j = 0; j < numevents; j++) {
      aeFileEvent* fe = &(ae->events[ae->fired[j].fd]);
      int fd = ae->fired[j].fd;

      jint _fds   [] = { fd       };
      jint _events[] = { fe->mask };

      (*env)->SetIntArrayRegion(env, fdsArray,    j, 1, _fds   );
      (*env)->SetIntArrayRegion(env, eventsArray, j, 1, _events);
    }
    return numevents;
}

inline static void io_vproxy_vfd_posix_GeneralPosix_aeCreateFileEvent0
  (jlong aex, jint fd, jint mask) {
    aeEventLoop* ae = (aeEventLoop*) aex;
    aeCreateFileEvent(ae, fd, mask, NULL, NULL);
}
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_aeCreateFileEvent0
  (JNIEnv* env, jclass self, jlong aex, jint fd, jint mask) {
#ifdef SHOW_CRITICAL
printf("normal Java_io_vproxy_vfd_posix_GeneralPosix_aeCreateFileEvent0\n");
#endif
    io_vproxy_vfd_posix_GeneralPosix_aeCreateFileEvent0(aex, fd, mask);
}
#ifdef USE_CRITICAL
JNIEXPORT void JNICALL JavaCritical_io_vproxy_vfd_posix_GeneralPosix_aeCreateFileEvent0
  (jlong aex, jint fd, jint mask) {
#ifdef SHOW_CRITICAL
printf("critical JavaCritical_io_vproxy_vfd_posix_GeneralPosix_aeCreateFileEvent0\n");
#endif
    io_vproxy_vfd_posix_GeneralPosix_aeCreateFileEvent0(aex, fd, mask);
}
#endif

inline static void io_vproxy_vfd_posix_GeneralPosix_aeUpdateFileEvent0
  (jlong aex, jint fd, jint mask) {
    aeEventLoop* ae = (aeEventLoop*) aex;
    aeDeleteFileEvent(ae, fd, 0xffffffff);
    aeCreateFileEvent(ae, fd, mask, NULL, NULL);
}
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_aeUpdateFileEvent0
  (JNIEnv* env, jclass self, jlong aex, jint fd, jint mask) {
#ifdef SHOW_CRITICAL
printf("normal Java_io_vproxy_vfd_posix_GeneralPosix_aeUpdateFileEvent0\n");
#endif
    io_vproxy_vfd_posix_GeneralPosix_aeUpdateFileEvent0(aex, fd, mask);
}
#ifdef USE_CRITICAL
JNIEXPORT void JNICALL JavaCritical_io_vproxy_vfd_posix_GeneralPosix_aeUpdateFileEvent0
  (jlong aex, jint fd, jint mask) {
#ifdef SHOW_CRITICAL
printf("critical JavaCritical_io_vproxy_vfd_posix_GeneralPosix_aeUpdateFileEvent0\n");
#endif
    io_vproxy_vfd_posix_GeneralPosix_aeUpdateFileEvent0(aex, fd, mask);
}
#endif

inline static void io_vproxy_vfd_posix_GeneralPosix_aeDeleteFileEvent0
  (jlong aex, jint fd) {
    aeEventLoop* ae = (aeEventLoop*) aex;
    aeDeleteFileEvent(ae, fd, 0xffffffff);
}
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_aeDeleteFileEvent0
  (JNIEnv* env, jclass self, jlong aex, jint fd) {
#ifdef SHOW_CRITICAL
printf("normal Java_io_vproxy_vfd_posix_GeneralPosix_aeDeleteFileEvent0\n");
#endif
    io_vproxy_vfd_posix_GeneralPosix_aeDeleteFileEvent0(aex, fd);
}
#ifdef USE_CRITICAL
JNIEXPORT void JNICALL JavaCritical_io_vproxy_vfd_posix_GeneralPosix_aeDeleteFileEvent0
  (jlong aex, jint fd) {
#ifdef SHOW_CRITICAL
printf("critical JavaCritical_io_vproxy_vfd_posix_GeneralPosix_aeDeleteFileEvent0\n");
#endif
    io_vproxy_vfd_posix_GeneralPosix_aeDeleteFileEvent0(aex, fd);
}
#endif

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_aeDeleteEventLoop
  (JNIEnv* env, jobject self, jlong aex) {
    aeEventLoop* ae = (aeEventLoop*) aex;
    // we need to release all the GlobalRefs
    for (int fd = 0; fd <= ae->maxfd; ++fd) {
        jobject ref = (jobject)aeGetClientData(ae, fd);
        if (ref != NULL) {
            (*env)->DeleteGlobalRef(env, ref);
        }
    }
    aeDeleteEventLoop(ae);
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_setBlocking
  (JNIEnv* env, jobject self, jint fd, jboolean v) {
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
  (JNIEnv* env, jobject self, jint fd, jint v) {
    v_linger sl;
    sl.l_onoff = 1;
    sl.l_linger = v;
    int res = v_setsockopt(fd, V_SOL_SOCKET, V_SO_LINGER, &sl, sizeof(v_linger));
    if (res < 0) {
        throwIOExceptionBasedOnErrno(env);
    }
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_setReusePort
  (JNIEnv* env, jobject self, jint fd, jboolean v) {
    #ifndef FSTACK
        int optval = v ? 1 : 0;
        int res = v_setsockopt(fd, V_SOL_SOCKET, V_SO_REUSEPORT, &optval, sizeof(int));
        if (res < 0) {
            throwIOExceptionBasedOnErrno(env);
        }
    #endif
    // do nothing for FSTACK
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_setRcvBuf
  (JNIEnv* env, jobject self, jint fd, jint v) {
    int val = v;
    int res = v_setsockopt(fd, V_SOL_SOCKET, V_SO_RCVBUF, &val, sizeof(int));
    if (res < 0) {
        throwIOExceptionBasedOnErrno(env);
    }
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_setTcpNoDelay
  (JNIEnv* env, jobject self, jint fd, jboolean v) {
    int i = v ? 1 : 0;
    int res = v_setsockopt(fd, V_IPPROTO_TCP, V_TCP_NODELAY, &i, sizeof(int));
    if (res < 0) {
        throwIOExceptionBasedOnErrno(env);
    }
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_setBroadcast
  (JNIEnv* env, jobject self, jint fd, jboolean v) {
    int i = v ? 1 : 0;
    int res = v_setsockopt(fd, V_SOL_SOCKET, V_SO_BROADCAST, &i, sizeof(int));
    if (res < 0) {
        throwIOExceptionBasedOnErrno(env);
    }
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_setIpTransparent
  (JNIEnv* env, jobject self, jint fd, jboolean v) {
    int i = v ? 1 : 0;
    int res = v_setsockopt(fd, V_SOL_IP, V_IP_TRANSPARENT, &i, sizeof(int));
    if (res < 0) {
      throwIOExceptionBasedOnErrno(env);
    }
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_close
  (JNIEnv* env, jobject self, jint fd) {
    int res = v_close(fd);
    if (res < 0) {
        throwIOExceptionBasedOnErrno(env);
    }
}

JNIEXPORT jint JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_createIPv4TcpFD
  (JNIEnv* env, jobject self) {
    int sockfd = v_socket(V_AF_INET, V_SOCK_STREAM, 0);
    if (sockfd < 0) {
        throwIOExceptionBasedOnErrno(env);
        return -1;
    }
    return sockfd;
}

JNIEXPORT jint JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_createIPv6TcpFD
  (JNIEnv* env, jobject self) {
    int sockfd6 = v_socket(V_AF_INET6, V_SOCK_STREAM, 0);
    if (sockfd6 < 0) {
        throwIOExceptionBasedOnErrno(env);
        return -1;
    }
    return sockfd6;
}

JNIEXPORT jint JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_createIPv4UdpFD
  (JNIEnv* env, jobject self) {
    int sockfd = v_socket(V_AF_INET, V_SOCK_DGRAM, 0);
    if (sockfd < 0) {
        throwIOExceptionBasedOnErrno(env);
        return -1;
    }
    return sockfd;
}

JNIEXPORT jint JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_createIPv6UdpFD
  (JNIEnv* env, jobject self) {
    int sockfd6 = v_socket(V_AF_INET6, V_SOCK_DGRAM, 0);
    if (sockfd6 < 0) {
        throwIOExceptionBasedOnErrno(env);
        return -1;
    }
    return sockfd6;
}

JNIEXPORT jint JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_createUnixDomainSocketFD
  (JNIEnv* env, jobject self) {
    int uds = v_socket(V_AF_UNIX, V_SOCK_STREAM, 0);
    if (uds < 0) {
      throwIOExceptionBasedOnErrno(env);
      return -1;
    }
    return uds;
}

void j2cSockAddrIPv4(v_sockaddr_in* name, jint addrHostOrder, jint port) {
    v_bzero(name, sizeof(v_sockaddr_in));
    name->sin_family = V_AF_INET;
    name->sin_port = v_htons(port);
    name->sin_addr.s_addr = v_htonl(addrHostOrder);
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_bindIPv4
  (JNIEnv* env, jobject self, jint fd, jint addrHostOrder, jint port) {
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

int j2cSockAddrIPv6(JNIEnv* env, v_sockaddr_in6* name, jstring fullAddr, jint port) {
    v_bzero(name, sizeof(v_sockaddr_in6));
    name->sin6_family = AF_INET6;
    name->sin6_port = htons(port);
    const char* fullAddrCharArray = (*env)->GetStringUTFChars(env, fullAddr, NULL);
    int res = v_inet_pton(V_AF_INET6, fullAddrCharArray, &(name->sin6_addr.s6_addr));
    (*env)->ReleaseStringUTFChars(env, fullAddr, fullAddrCharArray);
    return res;
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_bindIPv6
  (JNIEnv* env, jobject self, jint fd, jstring fullAddr, jint port) {
    v_sockaddr_in6 name;
    int res = j2cSockAddrIPv6(env, &name, fullAddr, port);
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
  (JNIEnv* env, jobject self, jint fd, jstring path) {
    const char* pathChars = (*env)->GetStringUTFChars(env, path, NULL);
    if (strlen(pathChars) >= UNIX_PATH_MAX) {
        (*env)->ReleaseStringUTFChars(env, path, pathChars);
        throwIOException(env, "path too long");
        return;
    }

    v_sockaddr_un uds_sockaddr;
    memset(&uds_sockaddr, 0, sizeof(uds_sockaddr));
    uds_sockaddr.sun_family = V_AF_UNIX;
    strcpy(uds_sockaddr.sun_path, pathChars);

    int res = v_bind(fd, (v_sockaddr*) &uds_sockaddr, sizeof(uds_sockaddr));
    if (res < 0) {
        (*env)->ReleaseStringUTFChars(env, path, pathChars);
        throwIOExceptionBasedOnErrno(env);
        return;
    }
    res = v_listen(fd, LISTEN_BACKLOG);
    if (res < 0) {
        (*env)->ReleaseStringUTFChars(env, path, pathChars);
        throwIOExceptionBasedOnErrno(env);
        return;
    }
    (*env)->ReleaseStringUTFChars(env, path, pathChars);
}

JNIEXPORT jint JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_accept
  (JNIEnv* env, jobject self, jint fd) {
    int ret = v_accept(fd, NULL, NULL);
    if (ret < 0) {
        if (errno == V_EAGAIN || errno == V_EWOULDBLOCK) {
            return 0;
        }
        throwIOExceptionBasedOnErrno(env);
        return 0;
    }
    return ret;
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_connectIPv4
  (JNIEnv* env, jobject self, jint fd, jint addrHostOrder, jint port) {
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
  (JNIEnv* env, jobject self, jint fd, jstring fullAddr, jint port) {
    v_sockaddr_in6 name;
    int res = j2cSockAddrIPv6(env, &name, fullAddr, port);
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
  (JNIEnv* env, jobject self, jint fd, jstring sock) {
    const char* sockChars = (*env)->GetStringUTFChars(env, sock, NULL);
    v_sockaddr_un addr;
    memset(&addr, 0, sizeof(v_sockaddr_un));
    addr.sun_family = V_AF_UNIX;
    strcpy(addr.sun_path, sockChars);
    int err = connect(fd, (struct sockaddr *) &addr, sizeof(v_sockaddr_un));
    (*env)->ReleaseStringUTFChars(env, sock, sockChars);

    if (err) {
        throwIOExceptionBasedOnErrno(env);
    }
}

jint handleWriteIOOperationResult(JNIEnv* env, int res) {
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
  (JNIEnv* env, jobject self, jint fd) {
    byte buf[0];
    int res = v_write(fd, 0, 0);
    handleWriteIOOperationResult(env, res);
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_shutdownOutput
  (JNIEnv* env, jobject self, jint fd) {
    int res = v_shutdown(fd, V_SHUT_WR);
    if (res < 0) {
        throwIOExceptionBasedOnErrno(env);
        return;
    }
}

jclass SocketAddressIPv4 = NULL;
jmethodID SocketAddressIPv4_init = NULL;

jobject formatSocketAddressIPv4(JNIEnv* env, v_sockaddr_in* addr) {
    jint ip = v_ntohl(addr->sin_addr.s_addr);
    jint port = v_ntohs(addr->sin_port);
    if (SocketAddressIPv4 == NULL) {
      jclass sCls = (*env)->FindClass(env, "io/vproxy/vfd/posix/SocketAddressIPv4");
      SocketAddressIPv4 = (jclass)(*env)->NewGlobalRef(env, (jobject)sCls);
    }
    if (SocketAddressIPv4_init == NULL) {
      SocketAddressIPv4_init = (*env)->GetMethodID(env, SocketAddressIPv4, "<init>", "(II)V");
    }
    jobject obj = (*env)->NewObject(env, SocketAddressIPv4, SocketAddressIPv4_init, ip, port);
    return obj;
}

jclass SocketAddressIPv6 = NULL;
jmethodID SocketAddressIPv6_init = NULL;

jobject formatSocketAddressIPv6(JNIEnv* env, v_sockaddr_in6* addr) {
    // build ip string
    char chars[40]; // 16bytes=32hex, 4hex in one group=7split, so 39 + 1(\0) is enough
    const char* res = v_inet_ntop(V_AF_INET6, &(addr->sin6_addr), chars, sizeof(chars));
    if (res == NULL) {
        throwIOExceptionBasedOnErrno(env);
        return NULL;
    }
    jstring strIp = (*env)->NewStringUTF(env, chars);
    // retrieve the port
    jint port = v_ntohs(addr->sin6_port);

    // build result
    if (SocketAddressIPv6 == NULL) {
      jclass sCls = (*env)->FindClass(env, "io/vproxy/vfd/posix/SocketAddressIPv6");
      SocketAddressIPv6 = (jclass)(*env)->NewGlobalRef(env, (jobject)sCls);
    }
    if (SocketAddressIPv6_init == NULL) {
      SocketAddressIPv6_init = (*env)->GetMethodID(env, SocketAddressIPv6, "<init>", "(Ljava/lang/String;I)V");
    }

    jobject obj = (*env)->NewObject(env, SocketAddressIPv6, SocketAddressIPv6_init, strIp, port);
    return obj;
}

jclass SocketAddressUDS = NULL;
jmethodID SocketAddressUDS_init = NULL;

jobject formatSocketAddressUDS(JNIEnv* env, v_sockaddr_un* addr) {
    char* path = addr->sun_path;
    jstring strPath = (*env)->NewStringUTF(env, path);

    if (SocketAddressUDS == NULL) {
      jclass sCls = (*env)->FindClass(env, "io/vproxy/vfd/posix/SocketAddressUDS");
      SocketAddressUDS = (jclass)(*env)->NewGlobalRef(env, (jobject)sCls);
    }
    if (SocketAddressUDS_init == NULL) {
      SocketAddressUDS_init = (*env)->GetMethodID(env, SocketAddressUDS, "<init>", "(Ljava/lang/String;)V");
    }
    jobject obj = (*env)->NewObject(env, SocketAddressUDS, SocketAddressUDS_init, strPath);
    return obj;
}

JNIEXPORT jobject JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_getIPv4Local
  (JNIEnv* env, jobject self, jint fd) {
    v_sockaddr_in name;
    unsigned int foo = sizeof(v_sockaddr_in);
    int res = v_getsockname(fd, (v_sockaddr*) &name, &foo);
    if (res < 0) {
        throwIOExceptionBasedOnErrno(env);
        return NULL;
    }
    return formatSocketAddressIPv4(env, &name);
}

JNIEXPORT jobject JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_getIPv6Local
  (JNIEnv* env, jobject self, jint fd) {
    v_sockaddr_in6 name;
    unsigned int foo = sizeof(v_sockaddr_in6);
    int res = v_getsockname(fd, (v_sockaddr*) &name, &foo);
    if (res < 0) {
        throwIOExceptionBasedOnErrno(env);
        return NULL;
    }
    return formatSocketAddressIPv6(env, &name);
}

JNIEXPORT jobject JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_getIPv4Remote
  (JNIEnv* env, jobject self, jint fd) {
    v_sockaddr_in name;
    unsigned int foo = sizeof(v_sockaddr_in);
    int res = v_getpeername(fd, (v_sockaddr*) &name, &foo);
    if (res < 0) {
        throwIOExceptionBasedOnErrno(env);
        return NULL;
    }
    return formatSocketAddressIPv4(env, &name);
}

JNIEXPORT jobject JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_getIPv6Remote
  (JNIEnv* env, jobject self, jint fd) {
    v_sockaddr_in6 name;
    unsigned int foo = sizeof(v_sockaddr_in6);
    int res = v_getpeername(fd, (v_sockaddr*) &name, &foo);
    if (res < 0) {
        throwIOExceptionBasedOnErrno(env);
        return NULL;
    }
    return formatSocketAddressIPv6(env, &name);
}

JNIEXPORT jobject JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_getUDSLocal
  (JNIEnv* env, jobject self, jint fd) {
    v_sockaddr_un name;
    memset(&name, 0, sizeof(name));
    unsigned int foo = sizeof(name);
    int res = v_getsockname(fd, (v_sockaddr*) &name, &foo);
    if (res < 0) {
        throwIOExceptionBasedOnErrno(env);
        return NULL;
    }
    return formatSocketAddressUDS(env, &name);
}

JNIEXPORT jobject JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_getUDSRemote
  (JNIEnv* env, jobject self, jint fd) {
    v_sockaddr_un name;
    memset(&name, 0, sizeof(name));
    unsigned int foo = sizeof(name);
    int res = v_getpeername(fd, (v_sockaddr*) &name, &foo);
    if (res < 0) {
        throwIOExceptionBasedOnErrno(env);
        return NULL;
    }
    return formatSocketAddressUDS(env, &name);
}

jint handleReadIOOperationResult(JNIEnv* env, int res) {
    if (res < 0) {
        if (errno == V_EAGAIN || errno == V_EWOULDBLOCK) {
            return 0;
        }
        throwIOExceptionBasedOnErrno(env);
        return 0;
    } else if (res == 0) { // EOF
        return -1;
    }
    return res;
}

JNIEXPORT jint JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_read
  (JNIEnv* env, jobject self, jint fd, jobject directBuffer, jint off, jint len) {
    if (len == 0) {
        return 0;
    }
    byte* buf = (*env)->GetDirectBufferAddress(env, directBuffer);
    int res = v_read(fd, buf + off, len);
    return handleReadIOOperationResult(env, res);
}

JNIEXPORT jint JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_write
  (JNIEnv* env, jobject self, jint fd, jobject directBuffer, jint off, jint len) {
    if (len == 0) {
        return 0;
    }
    byte* buf = (*env)->GetDirectBufferAddress(env, directBuffer);
    int res = v_write(fd, buf + off, len);
    return handleWriteIOOperationResult(env, res);
}

JNIEXPORT jint JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_sendtoIPv4
  (JNIEnv* env, jobject self, jint fd, jobject directBuffer, jint off, jint len, jint addrHostOrder, jint port) {
    if (len == 0) {
        return 0;
    }
    byte* buf = (*env)->GetDirectBufferAddress(env, directBuffer);
    v_sockaddr_in name;
    j2cSockAddrIPv4(&name, addrHostOrder, port);
    int res = v_sendto(fd, buf + off, len, 0, (v_sockaddr*) &name, sizeof(v_sockaddr_in));
    return handleWriteIOOperationResult(env, res);
}

JNIEXPORT jint JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_sendtoIPv6
  (JNIEnv* env, jobject self, jint fd, jobject directBuffer, jint off, jint len, jstring fullAddr, jint port) {
    if (len == 0) {
        return 0;
    }
    byte* buf = (*env)->GetDirectBufferAddress(env, directBuffer);
    v_sockaddr_in6 name;
    int res = j2cSockAddrIPv6(env, &name, fullAddr, port);
    if (res < 0) {
        throwIOExceptionBasedOnErrno(env);
        return 0;
    }
    res = v_sendto(fd, buf + off, len, 0, (v_sockaddr*) &name, sizeof(v_sockaddr_in6));
    return handleWriteIOOperationResult(env, res);
}

jclass UDPRecvResult = NULL;
jmethodID UDPRecvResult_init = NULL;

JNIEXPORT jobject JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_recvfromIPv4
  (JNIEnv* env, jobject self, jint fd, jobject directBuffer, jint off, jint len) {
    if (len == 0) {
        return NULL;
    }
    byte* buf = (*env)->GetDirectBufferAddress(env, directBuffer);
    v_sockaddr_in name;
    unsigned int foo = sizeof(v_sockaddr_in);
    int res = v_recvfrom(fd, buf + off, len, 0, (v_sockaddr*) &name, &foo);
    jint retLen = handleReadIOOperationResult(env, res);
    if (res < 0) {
        return NULL;
    }
    jobject retAddr = formatSocketAddressIPv4(env, &name);

    if (UDPRecvResult == NULL) {
      jclass sCls = (*env)->FindClass(env, "io/vproxy/vfd/posix/UDPRecvResult");
      UDPRecvResult = (jclass)(*env)->NewGlobalRef(env, (jobject)sCls);
    }
    if (UDPRecvResult_init == NULL) {
      UDPRecvResult_init = (*env)->GetMethodID(env, UDPRecvResult, "<init>", "(Lio/vproxy/vfd/posix/VSocketAddress;I)V");
    }
    jobject ret = (*env)->NewObject(env, UDPRecvResult, UDPRecvResult_init, retAddr, retLen);
    return ret;
}

JNIEXPORT jobject JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_recvfromIPv6
  (JNIEnv* env, jobject self, jint fd, jobject directBuffer, jint off, jint len) {
    if (len == 0) {
        return NULL;
    }
    byte* buf = (*env)->GetDirectBufferAddress(env, directBuffer);
    v_sockaddr_in6 name;
    unsigned int foo = sizeof(v_sockaddr_in6);
    int res = v_recvfrom(fd, buf + off, len, 0, (v_sockaddr*) &name, &foo);
    jint retLen = handleReadIOOperationResult(env, res);
    if (res < 0) {
        return NULL;
    }
    jobject retAddr = formatSocketAddressIPv6(env, &name);
    if (retAddr == NULL) {
        return NULL;
    }

    if (UDPRecvResult == NULL) {
      jclass sCls = (*env)->FindClass(env, "io/vproxy/vfd/posix/UDPRecvResult");
      UDPRecvResult = (jclass)(*env)->NewGlobalRef(env, (jobject)sCls);
    }
    if (UDPRecvResult_init == NULL) {
      UDPRecvResult_init = (*env)->GetMethodID(env, UDPRecvResult, "<init>", "(Lio/vproxy/vfd/posix/VSocketAddress;I)V");
    }
    jobject ret = (*env)->NewObject(env, UDPRecvResult, UDPRecvResult_init, retAddr, retLen);
    return ret;
}

JNIEXPORT jlong JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_currentTimeMillis
  (JNIEnv* env, jobject self) {
    v_timeval tv;
    v_gettimeofday(&tv, NULL);
    return ((long)tv.tv_sec) * 1000 + tv.tv_usec / 1000;
}

JNIEXPORT jboolean JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_tapNonBlockingSupported
  (JNIEnv* env, jobject self) {
    #ifdef __linux__
      return JNI_TRUE;
    #elif defined(__APPLE__)
      return JNI_FALSE;
    #else
      throwIOException(env, "unsupported on current platform");
    #endif
}

JNIEXPORT jboolean JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_tunNonBlockingSupported
  (JNIEnv* env, jobject self) {
    #ifdef __linux__
      return JNI_TRUE;
    #elif defined(__APPLE__)
      return JNI_TRUE;
    #else
      throwIOException(env, "unsupported on current platform");
    #endif
}

jclass TapInfo = NULL;
jmethodID TapInfo_init = NULL;

JNIEXPORT jobject JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_createTapFD
  (JNIEnv* env, jobject self, jstring dev, jboolean isTun) {
    // the returned device name
    char devName[IFNAMSIZ + 1];
    // get java input dev name
    const char* devChars = (*env)->GetStringUTFChars(env, dev, NULL);
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
      strncpy(ifr.ifr_name, devChars, IFNAMSIZ);

      if (ioctl(fd, TUNSETIFF, (void *) &ifr) < 0) {
          goto fail;
      }

      strcpy(devName, ifr.ifr_name);

    // end ifdef __linux__
    #elif defined(__APPLE__)

    if (isTun) {
      // use macos utun
      if (!v_str_starts_with(devChars, "utun")) {
        (*env)->ReleaseStringUTFChars(env, dev, devChars);
        throwIOException(env, "tun devices must starts with `utun`");
        return NULL;
      }
      int utun = atoi(devChars + 4); // 4 for "utun"
      if (utun < 0) {
        (*env)->ReleaseStringUTFChars(env, dev, devChars);
        throwIOException(env, "tun devices must be utun{n} where n >= 0");
        return NULL;
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
        (*env)->ReleaseStringUTFChars(env, dev, devChars);
        throwIOException(env, "input dev name is too long");
        return NULL;
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
      strncpy(ifr.ifr_name, devChars, IFNAMSIZ);
      if (v_ioctl(tmpFd, V_SIOCGIFFLAGS, &ifr) < 0) {
          goto fail;
      }
      ifr.ifr_flags |= IFF_UP;
      if (v_ioctl(tmpFd, V_SIOCSIFFLAGS, &ifr) < 0) {
          goto fail;
      }
    }
      // copy the dev name to return str
      strncpy(devName, devChars, IFNAMSIZ);

    // end defined(__APPLE__)
    #else
      (*env)->ReleaseStringUTFChars(env, dev, devChars);
      throwIOException(env, "unsupported on current platform");
      return NULL;
    #endif

      devName[IFNAMSIZ] = '\0'; // make sure the last byte is 0
      jstring genDevName = (*env)->NewStringUTF(env, devName);

      if (TapInfo == NULL) {
        jclass sCls = (*env)->FindClass(env, "io/vproxy/vfd/TapInfo");
        TapInfo = (jclass)(*env)->NewGlobalRef(env, (jobject)sCls);
      }
      if (TapInfo_init == NULL) {
        TapInfo_init = (*env)->GetMethodID(env, TapInfo, "<init>", "(Ljava/lang/String;I)V");
      }
      jobject ret = (*env)->NewObject(env, TapInfo, TapInfo_init, genDevName, fd);

      (*env)->ReleaseStringUTFChars(env, dev, devChars);
      if (tmpFd > 0) {
          v_close(tmpFd);
      }
      return ret;
fail:
      if (fd > 0) {
          v_close(fd);
      }
      if (tmpFd > 0) {
          v_close(tmpFd);
      }
      (*env)->ReleaseStringUTFChars(env, dev, devChars);
      throwIOExceptionBasedOnErrno(env);
      return NULL;
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_setCoreAffinityForCurrentThread
  (JNIEnv* env, jobject self, jlong mask) {
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

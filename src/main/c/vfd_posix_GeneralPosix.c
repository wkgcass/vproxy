#include "vfd_posix_GeneralPosix.h"
#include "vfd_posix.h"
#include "exception.h"

#define MAX_EVENTS 512

JNIEXPORT jboolean JNICALL Java_vfd_posix_GeneralPosix_pipeFDSupported
  (JNIEnv* env, jobject self) {
    #ifdef HAVE_FF_KQUEUE
        return JNI_FALSE;
    #else
        return JNI_TRUE;
    #endif
}

JNIEXPORT jboolean JNICALL Java_vfd_posix_GeneralPosix_onlySelectNow
  (JNIEnv* env, jobject self) {
    #ifdef HAVE_FF_KQUEUE
        return JNI_TRUE;
    #else
        return JNI_FALSE;
    #endif
}

JNIEXPORT jint JNICALL Java_vfd_posix_GeneralPosix_aeReadable
  (JNIEnv* env, jobject self) {
    return AE_READABLE;
}

JNIEXPORT jint JNICALL Java_vfd_posix_GeneralPosix_aeWritable
  (JNIEnv* env, jobject self) {
    return AE_WRITABLE;
}

JNIEXPORT jintArray JNICALL Java_vfd_posix_GeneralPosix_openPipe
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

JNIEXPORT jlong JNICALL Java_vfd_posix_GeneralPosix_aeCreateEventLoop
  (JNIEnv* env, jobject self, jint setsize) {
    aeEventLoop* ae = aeCreateEventLoop(setsize);
    if (ae == NULL) {
        throwIOException(env, "create ae failed");
    }
    return (jlong)ae;
}

// return FDInfo[]
JNIEXPORT jobjectArray JNICALL Java_vfd_posix_GeneralPosix_aeApiPoll
  (JNIEnv* env, jobject self, jlong aex, jlong wait) {
    aeEventLoop* ae = (aeEventLoop*) aex;
    v_timeval tv;
    v_timeval* tvp = &tv;
    tvp->tv_sec = wait/1000;
    tvp->tv_usec = (wait % 1000)*1000;
    int numevents = aePoll(ae, tvp);

    jclass fdInfoCls = (*env)->FindClass(env, "vfd/posix/FDInfo");
    jmethodID constructor = (*env)->GetMethodID(env, fdInfoCls, "<init>", "(IILjava/lang/Object;)V");

    jobjectArray ret = (*env)->NewObjectArray(env, numevents, fdInfoCls, NULL);
    for (int j = 0; j < numevents; j++) {
      aeFileEvent* fe = &(ae->events[ae->fired[j].fd]);
      int mask = ae->fired[j].mask;
      int fd = ae->fired[j].fd;
      void* clientData = fe->clientData;

      jobject obj = (*env)->NewObject(env, fdInfoCls, constructor, fd, mask, (jobject) clientData);
      (*env)->SetObjectArrayElement(env, ret, j, obj);
    }
    return ret;
}

// return FDInfo[]
JNIEXPORT jobjectArray JNICALL Java_vfd_posix_GeneralPosix_aeAllFDs
  (JNIEnv* env, jobject self, jlong aex) {
    aeEventLoop* ae = (aeEventLoop*) aex;
    int cnt = 0;
    for (int i = 0; i < ae->maxfd; ++i) {
      if ((&(ae->events[i]))->clientData != NULL) {
        ++cnt;
      }
    }
    jclass fdInfoCls = (*env)->FindClass(env, "vfd/posix/FDInfo");
    jmethodID constructor = (*env)->GetMethodID(env, fdInfoCls, "<init>", "(IILjava/lang/Object;)V");

    jobjectArray ret = (*env)->NewObjectArray(env, cnt, fdInfoCls, NULL);
    cnt = 0;
    for (int fd = 0; fd < ae->maxfd; ++fd) {
      aeFileEvent* fe = &(ae->events[fd]);
      if (fe->clientData != NULL) {
        jobject obj = (*env)->NewObject(env, fdInfoCls, constructor, fd, fe->mask, (jobject) fe->clientData);
        (*env)->SetObjectArrayElement(env, ret, cnt, obj);
        ++cnt;
      }
    }
    return ret;
}

JNIEXPORT void JNICALL Java_vfd_posix_GeneralPosix_aeCreateFileEvent
  (JNIEnv* env, jobject self, jlong aex, jint fd, jint mask, jobject clientData) {
    aeEventLoop* ae = (aeEventLoop*) aex;
    jobject ref = (*env)->NewGlobalRef(env, clientData);
    aeCreateFileEvent(ae, fd, mask, NULL, ref);
}

JNIEXPORT void JNICALL Java_vfd_posix_GeneralPosix_aeUpdateFileEvent
  (JNIEnv* env, jobject self, jlong aex, jint fd, jint mask) {
    aeEventLoop* ae = (aeEventLoop*) aex;
    void* ref = aeGetClientData(ae, fd);
    if (ref == NULL) {
        // fd not registered
        return;
    }
    aeDeleteFileEvent(ae, fd, 0xffffffff);
    aeCreateFileEvent(ae, fd, mask, NULL, ref);
    // mainly to reduce the cost of creating and releasing a java GlobalRef
}

JNIEXPORT void JNICALL Java_vfd_posix_GeneralPosix_aeDeleteFileEvent
  (JNIEnv* env, jobject self, jlong aex, jint fd) {
    aeEventLoop* ae = (aeEventLoop*) aex;
    jobject ref = (jobject) aeGetClientData(ae, fd);
    if (ref != NULL) {
        (*env)->DeleteGlobalRef(env, ref);
    }
    aeDeleteFileEvent(ae, fd, 0xffffffff);
}

JNIEXPORT jint JNICALL Java_vfd_posix_GeneralPosix_aeGetFileEvents
  (JNIEnv* env, jobject self, jlong aex, jint fd) {
    aeEventLoop* ae = (aeEventLoop*) aex;
    return aeGetFileEvents(ae, fd);
}

JNIEXPORT jobject JNICALL Java_vfd_posix_GeneralPosix_aeGetClientData
  (JNIEnv* env, jobject self, jlong aex, jint fd) {
    aeEventLoop* ae = (aeEventLoop*) aex;
    return aeGetClientData(ae, fd);
}

JNIEXPORT void JNICALL Java_vfd_posix_GeneralPosix_aeDeleteEventLoop
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

JNIEXPORT void JNICALL Java_vfd_posix_GeneralPosix_setBlocking
  (JNIEnv* env, jobject self, jint fd, jboolean v) {
    int on = 1;
    int res = v_ioctl(fd, V_FIONBIO, &on);
    if (res == -1) {
        throwIOExceptionBasedOnErrno(env);
        return;
    }
}

JNIEXPORT void JNICALL Java_vfd_posix_GeneralPosix_setSoLinger
  (JNIEnv* env, jobject self, jint fd, jint v) {
    v_linger sl;
    sl.l_onoff = 1;
    sl.l_linger = v;
    int res = v_setsockopt(fd, V_SOL_SOCKET, V_SO_LINGER, &sl, sizeof(v_linger));
    if (res < 0) {
        throwIOExceptionBasedOnErrno(env);
    }
}

JNIEXPORT void JNICALL Java_vfd_posix_GeneralPosix_setReusePort
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

JNIEXPORT void JNICALL Java_vfd_posix_GeneralPosix_setTcpNoDelay
  (JNIEnv* env, jobject self, jint fd, jboolean v) {
    int i = v ? 1 : 0;
    int res = v_setsockopt(fd, V_IPPROTO_TCP, V_TCP_NODELAY, &i, sizeof(int));
    if (res < 0) {
        throwIOExceptionBasedOnErrno(env);
    }
}

JNIEXPORT void JNICALL Java_vfd_posix_GeneralPosix_close
  (JNIEnv* env, jobject self, jint fd) {
    int res = v_close(fd);
    if (res < 0) {
        throwIOExceptionBasedOnErrno(env);
    }
}

JNIEXPORT jint JNICALL Java_vfd_posix_GeneralPosix_createIPv4TcpFD
  (JNIEnv* env, jobject self) {
    int sockfd = v_socket(V_AF_INET, V_SOCK_STREAM, 0);
    if (sockfd < 0) {
        throwIOExceptionBasedOnErrno(env);
        return -1;
    }
    return sockfd;
}

JNIEXPORT jint JNICALL Java_vfd_posix_GeneralPosix_createIPv6TcpFD
  (JNIEnv* env, jobject self) {
    int sockfd6 = v_socket(V_AF_INET6, V_SOCK_STREAM, 0);
    if (sockfd6 < 0) {
        throwIOExceptionBasedOnErrno(env);
        return -1;
    }
    return sockfd6;
}

JNIEXPORT jint JNICALL Java_vfd_posix_GeneralPosix_createIPv4UdpFD
  (JNIEnv* env, jobject self) {
    int sockfd = v_socket(V_AF_INET, V_SOCK_DGRAM, 0);
    if (sockfd < 0) {
        throwIOExceptionBasedOnErrno(env);
        return -1;
    }
    return sockfd;
}

JNIEXPORT jint JNICALL Java_vfd_posix_GeneralPosix_createIPv6UdpFD
  (JNIEnv* env, jobject self) {
    int sockfd6 = v_socket(V_AF_INET6, V_SOCK_DGRAM, 0);
    if (sockfd6 < 0) {
        throwIOExceptionBasedOnErrno(env);
        return -1;
    }
    return sockfd6;
}

void j2cSockAddrIPv4(v_sockaddr_in* name, jint addrHostOrder, jint port) {
    v_bzero(name, sizeof(v_sockaddr_in));
    name->sin_family = V_AF_INET;
    name->sin_port = v_htons(port);
    name->sin_addr.s_addr = v_htonl(addrHostOrder);
}

JNIEXPORT void JNICALL Java_vfd_posix_GeneralPosix_bindIPv4
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
    res = v_listen(fd, MAX_EVENTS);
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

JNIEXPORT void JNICALL Java_vfd_posix_GeneralPosix_bindIPv6
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
    res = v_listen(fd, MAX_EVENTS);
	if (res < 0) {
        throwIOExceptionBasedOnErrno(env);
        return;
    }
}

JNIEXPORT jint JNICALL Java_vfd_posix_GeneralPosix_accept
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

JNIEXPORT void JNICALL Java_vfd_posix_GeneralPosix_connectIPv4
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

JNIEXPORT void JNICALL Java_vfd_posix_GeneralPosix_connectIPv6
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

JNIEXPORT void JNICALL Java_vfd_posix_GeneralPosix_shutdownOutput
  (JNIEnv* env, jobject self, jint fd) {
    int res = v_shutdown(fd, V_SHUT_WR);
    if (res < 0) {
        throwIOExceptionBasedOnErrno(env);
        return;
    }
}

jobject formatSocketAddressIPv4(JNIEnv* env, v_sockaddr_in* addr) {
    jint ip = v_ntohl(addr->sin_addr.s_addr);
    jint port = v_ntohs(addr->sin_port);
    jclass sCls = (*env)->FindClass(env, "vfd/posix/SocketAddressIPv4");
    jmethodID constructor = (*env)->GetMethodID(env, sCls, "<init>", "(II)V");
    jobject obj = (*env)->NewObject(env, sCls, constructor, ip, port);
    return obj;
}

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
    jclass sCls = (*env)->FindClass(env, "vfd/posix/SocketAddressIPv6");
    jmethodID constructor = (*env)->GetMethodID(env, sCls, "<init>", "(Ljava/lang/String;I)V");

    jobject obj = (*env)->NewObject(env, sCls, constructor, strIp, port);
    return obj;
}

JNIEXPORT jobject JNICALL Java_vfd_posix_GeneralPosix_getIPv4Local
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

JNIEXPORT jobject JNICALL Java_vfd_posix_GeneralPosix_getIPv6Local
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

JNIEXPORT jobject JNICALL Java_vfd_posix_GeneralPosix_getIPv4Remote
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

JNIEXPORT jobject JNICALL Java_vfd_posix_GeneralPosix_getIPv6Remote
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

JNIEXPORT jint JNICALL Java_vfd_posix_GeneralPosix_read
  (JNIEnv* env, jobject self, jint fd, jobject directBuffer, jint off, jint len) {
    if (len == 0) {
        return 0;
    }
    byte* buf = (*env)->GetDirectBufferAddress(env, directBuffer);
    int res = v_read(fd, buf + off, len);
    return handleReadIOOperationResult(env, res);
}

JNIEXPORT jint JNICALL Java_vfd_posix_GeneralPosix_write
  (JNIEnv* env, jobject self, jint fd, jobject directBuffer, jint off, jint len) {
    if (len == 0) {
        return 0;
    }
    byte* buf = (*env)->GetDirectBufferAddress(env, directBuffer);
    int res = v_write(fd, buf + off, len);
    return handleWriteIOOperationResult(env, res);
}

JNIEXPORT jint JNICALL Java_vfd_posix_GeneralPosix_sendtoIPv4
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

JNIEXPORT jint JNICALL Java_vfd_posix_GeneralPosix_sendtoIPv6
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

JNIEXPORT jobject JNICALL Java_vfd_posix_GeneralPosix_recvfromIPv4
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

    jclass sCls = (*env)->FindClass(env, "vfd/posix/UDPRecvResult");
    jmethodID constructor = (*env)->GetMethodID(env, sCls, "<init>", "(Lvfd/posix/VSocketAddress;I)V");
    jobject ret = (*env)->NewObject(env, sCls, constructor, retAddr, retLen);
    return ret;
}

JNIEXPORT jobject JNICALL Java_vfd_posix_GeneralPosix_recvfromIPv6
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

    jclass sCls = (*env)->FindClass(env, "vfd/posix/UDPRecvResult");
    jmethodID constructor = (*env)->GetMethodID(env, sCls, "<init>", "(Lvfd/posix/VSocketAddress;I)V");
    jobject ret = (*env)->NewObject(env, sCls, constructor, retAddr, retLen);
    return ret;
}

JNIEXPORT jlong JNICALL Java_vfd_posix_GeneralPosix_currentTimeMillis
  (JNIEnv* env, jobject self) {
    v_timeval tv;
    v_gettimeofday(&tv, NULL);
    return ((long)tv.tv_sec) * 1000 + tv.tv_usec / 1000;
}

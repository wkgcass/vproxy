// modified from jni generated file
#include <jni.h>
#include "env.h"
#include "vfd_posix.h"
/* Header for class io_vproxy_vfd_posix_GeneralPosix */

#ifndef _Included_io_vproxy_vfd_posix_GeneralPosix
#define _Included_io_vproxy_vfd_posix_GeneralPosix
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    pipeFDSupported
 * Signature: ()Z
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_pipeFDSupported
  (JEnv *);

/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    onlySelectNow
 * Signature: ()Z
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_onlySelectNow
  (JEnv *);

/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    aeReadable
 * Signature: ()I
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_aeReadable
  (JEnv *);

/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    aeWritable
 * Signature: ()I
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_aeWritable
  (JEnv *);

/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    openPipe
 * Signature: ()[I
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_openPipe
  (JEnv *, uint32_t *);

/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    aeCreateEventLoop
 * Signature: (IZ)J
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_aeCreateEventLoop
  (JEnv *, uint32_t, uint8_t);

/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    aeApiPoll0
 * Signature: (JJ[I[I)I
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_aeApiPoll
  (JEnv *, void *, uint64_t, uint32_t *, uint32_t *);

/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    aeApiPollNow0
 * Signature: (J[I[I)I
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_aeApiPollNow
  (JEnv *, void *, uint32_t *, uint32_t *);

/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    aeCreateFileEvent0
 * Signature: (JII)V
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_aeCreateFileEvent
  (JEnv *, void *, jint, jint);

/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    aeUpdateFileEvent0
 * Signature: (JII)V
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_aeUpdateFileEvent
  (JEnv *, void *, jint, jint);

/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    aeDeleteFileEvent0
 * Signature: (JI)V
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_aeDeleteFileEvent
  (JEnv *, void *, jint);

/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    aeDeleteEventLoop
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_aeDeleteEventLoop
  (JEnv *, void *);

/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    setBlocking
 * Signature: (IZ)V
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_setBlocking
  (JEnv *, uint32_t, uint8_t);

/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    setSoLinger
 * Signature: (II)V
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_setSoLinger
  (JEnv *, uint32_t, int32_t);

/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    setReusePort
 * Signature: (IZ)V
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_setReusePort
  (JEnv *, uint32_t, uint8_t);

/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    setRcvBuf
 * Signature: (II)V
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_setRcvBuf
  (JEnv *, uint32_t, uint32_t);

/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    setTcpNoDelay
 * Signature: (IZ)V
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_setTcpNoDelay
  (JEnv *, uint32_t, uint8_t);

/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    setBroadcast
 * Signature: (IZ)V
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_setBroadcast
  (JEnv *, uint32_t, uint8_t);

/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    setIpTransparent
 * Signature: (IZ)V
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_setIpTransparent
  (JEnv *, uint32_t, uint8_t);

/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    close
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_close
  (JEnv *, uint32_t);

/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    createIPv4TcpFD
 * Signature: ()I
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_createIPv4TcpFD
  (JEnv *);

/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    createIPv6TcpFD
 * Signature: ()I
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_createIPv6TcpFD
  (JEnv *);

/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    createIPv4UdpFD
 * Signature: ()I
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_createIPv4UdpFD
  (JEnv *);

/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    createIPv6UdpFD
 * Signature: ()I
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_createIPv6UdpFD
  (JEnv *);

/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    createUnixDomainSocketFD
 * Signature: ()I
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_createUnixDomainSocketFD
  (JEnv *);

/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    bindIPv4
 * Signature: (III)V
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_bindIPv4
  (JEnv *, uint32_t, uint32_t, uint32_t);

/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    bindIPv6
 * Signature: (ILjava/lang/String;I)V
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_bindIPv6
  (JEnv *, uint32_t, char *, uint32_t);

/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    bindUnixDomainSocket
 * Signature: (ILjava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_bindUnixDomainSocket
  (JEnv *, uint32_t, char *);

/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    accept
 * Signature: (I)I
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_accept
  (JEnv *, uint32_t);

/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    connectIPv4
 * Signature: (III)V
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_connectIPv4
  (JEnv *, uint32_t, uint32_t, uint32_t);

/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    connectIPv6
 * Signature: (ILjava/lang/String;I)V
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_connectIPv6
  (JEnv *, uint32_t, char *, uint32_t);

/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    connectUDS
 * Signature: (ILjava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_connectUDS
  (JEnv *, uint32_t, char *);

/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    finishConnect
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_finishConnect
  (JEnv *, uint32_t);

/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    shutdownOutput
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_shutdownOutput
  (JEnv *, uint32_t);

typedef struct {
  uint32_t ip;
  uint16_t port;
} __attribute__((packed)) SocketAddressIPv4_st;

/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    getIPv4Local
 * Signature: (I)Lio/vproxy/vfd/posix/VSocketAddress;
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_getIPv4Local
  (JEnv *, uint32_t, SocketAddressIPv4_st *);

typedef struct {
  char     ip[40];
  uint16_t port;
} __attribute__((packed)) SocketAddressIPv6_st;

/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    getIPv6Local
 * Signature: (I)Lio/vproxy/vfd/posix/VSocketAddress;
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_getIPv6Local
  (JEnv *, uint32_t, SocketAddressIPv6_st *);

/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    getIPv4Remote
 * Signature: (I)Lio/vproxy/vfd/posix/VSocketAddress;
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_getIPv4Remote
  (JEnv *, uint32_t, SocketAddressIPv4_st *);

/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    getIPv6Remote
 * Signature: (I)Lio/vproxy/vfd/posix/VSocketAddress;
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_getIPv6Remote
  (JEnv *, uint32_t, SocketAddressIPv6_st *);

typedef struct {
  char path[4096];
} __attribute__((packed)) SocketAddressUDS_st;

/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    getUDSLocal
 * Signature: (I)Lio/vproxy/vfd/posix/VSocketAddress;
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_getUDSLocal
  (JEnv *, uint32_t, SocketAddressUDS_st *);

/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    getUDSRemote
 * Signature: (I)Lio/vproxy/vfd/posix/VSocketAddress;
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_getUDSRemote
  (JEnv *, uint32_t, SocketAddressUDS_st *);

/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    read
 * Signature: (ILjava/nio/ByteBuffer;II)I
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_read
  (JEnv *, uint32_t, void *, uint32_t, uint32_t);

/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    write
 * Signature: (ILjava/nio/ByteBuffer;II)I
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_write
  (JEnv *, uint32_t, void *, uint32_t, uint32_t);

/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    sendtoIPv4
 * Signature: (ILjava/nio/ByteBuffer;IIII)I
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_sendtoIPv4
  (JEnv *, uint32_t, void *, uint32_t, uint32_t, uint32_t, uint32_t);

/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    sendtoIPv6
 * Signature: (ILjava/nio/ByteBuffer;IILjava/lang/String;I)I
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_sendtoIPv6
  (JEnv *, uint32_t, void *, uint32_t, uint32_t, char *, uint32_t);

typedef struct {
  SocketAddressIPv4_st addr;
  uint32_t len;
} __attribute__((packed)) UDPRecvResultIPv4_st;

/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    recvfromIPv4
 * Signature: (ILjava/nio/ByteBuffer;II)Lio/vproxy/vfd/posix/UDPRecvResult;
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_recvfromIPv4
  (JEnv *, uint32_t, void *, uint32_t, uint32_t, UDPRecvResultIPv4_st *);

typedef struct {
  SocketAddressIPv6_st addr;
  uint32_t len;
} __attribute__((packed)) UDPRecvResultIPv6_st;

/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    recvfromIPv6
 * Signature: (ILjava/nio/ByteBuffer;II)Lio/vproxy/vfd/posix/UDPRecvResult;
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_recvfromIPv6
  (JEnv *, jint, jlong, jint, jint, UDPRecvResultIPv6_st *);

/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    currentTimeMillis
 * Signature: ()J
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_currentTimeMillis
  (JEnv *);

/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    tapNonBlockingSupported
 * Signature: ()Z
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_tapNonBlockingSupported
  (JEnv *);

/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    tunNonBlockingSupported
 * Signature: ()Z
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_tunNonBlockingSupported
  (JEnv *);

typedef struct {
  char devName[IFNAMSIZ];
  uint32_t fd;
} __attribute__((packed)) TapInfo_st;

/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    createTapFD
 * Signature: (Ljava/lang/String;Z)Lio/vproxy/vfd/TapInfo;
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_createTapFD
  (JEnv *, char *, jboolean, TapInfo_st *);

/*
 * Class:     io_vproxy_vfd_posix_GeneralPosix
 * Method:    setCoreAffinityForCurrentThread
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_io_vproxy_vfd_posix_GeneralPosix_setCoreAffinityForCurrentThread
  (JEnv *, jlong);

#ifdef __cplusplus
}
#endif
#endif

/* DO NOT EDIT THIS FILE - it is machine generated */
/* Header for class io_vproxy_vfd_posix_UDPRecvResultIPv4ST */
#ifndef _Included_io_vproxy_vfd_posix_UDPRecvResultIPv4ST
#define _Included_io_vproxy_vfd_posix_UDPRecvResultIPv4ST
#ifdef __cplusplus
extern "C" {
#endif

struct UDPRecvResultIPv4_st;
typedef struct UDPRecvResultIPv4_st UDPRecvResultIPv4_st;

#ifdef __cplusplus
}
#endif

#include <jni.h>
#include <pni.h>
#include "io_vproxy_vfd_posix_SocketAddressIPv4ST.h"

#ifdef __cplusplus
extern "C" {
#endif

PNIEnvExpand(UDPRecvResultIPv4_st, UDPRecvResultIPv4_st *)
PNIBufExpand(UDPRecvResultIPv4_st, UDPRecvResultIPv4_st, 12)

struct UDPRecvResultIPv4_st {
    SocketAddressIPv4_st addr;
    uint32_t len;
};

#ifdef __cplusplus
}
#endif
#endif // _Included_io_vproxy_vfd_posix_UDPRecvResultIPv4ST
// metadata.generator-version: pni 21.0.0.13
// sha256:46ab9dd0e78e5b901fc99b632d705bbbb64f71b29bdc47ea804a4bf7779c7ac4

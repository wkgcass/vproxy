/* DO NOT EDIT THIS FILE - it is machine generated */
/* Header for class io_vproxy_vfd_posix_UDPRecvResultIPv6ST */
#ifndef _Included_io_vproxy_vfd_posix_UDPRecvResultIPv6ST
#define _Included_io_vproxy_vfd_posix_UDPRecvResultIPv6ST
#ifdef __cplusplus
extern "C" {
#endif

struct UDPRecvResultIPv6_st;
typedef struct UDPRecvResultIPv6_st UDPRecvResultIPv6_st;

#ifdef __cplusplus
}
#endif

#include <jni.h>
#include <pni.h>
#include "io_vproxy_vfd_posix_SocketAddressIPv6ST.h"

#ifdef __cplusplus
extern "C" {
#endif

PNIEnvExpand(UDPRecvResultIPv6_st, UDPRecvResultIPv6_st *)

PNI_PACK(struct, UDPRecvResultIPv6_st, {
    SocketAddressIPv6_st addr; /* padding */ uint64_t :16;
    uint32_t len;
});

#ifdef __cplusplus
}
#endif
#endif // _Included_io_vproxy_vfd_posix_UDPRecvResultIPv6ST
// metadata.generator-version: pni 21.0.0.8
// sha256:d333c533a53179d0fdc4becc3bf49a1c80987b2519918c59f5d247effca8b7a6
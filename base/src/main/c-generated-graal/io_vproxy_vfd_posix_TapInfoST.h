/* DO NOT EDIT THIS FILE - it is machine generated */
/* Header for class io_vproxy_vfd_posix_TapInfoST */
#ifndef _Included_io_vproxy_vfd_posix_TapInfoST
#define _Included_io_vproxy_vfd_posix_TapInfoST
#ifdef __cplusplus
extern "C" {
#endif

struct TapInfo_st;
typedef struct TapInfo_st TapInfo_st;

#ifdef __cplusplus
}
#endif

#include <jni.h>
#include <pni.h>

#ifdef __cplusplus
extern "C" {
#endif

PNIEnvExpand(TapInfo_st, TapInfo_st *)
PNIBufExpand(TapInfo_st, TapInfo_st, 20)

struct TapInfo_st {
    char devName[16];
    int32_t fd;
};

#ifdef __cplusplus
}
#endif
#endif // _Included_io_vproxy_vfd_posix_TapInfoST
// metadata.generator-version: pni 22.0.0.17
// sha256:27ef2ca07c5b11a5308408db76c9d88ddc07dd81ef4c623eb040cd0883d0aa4f

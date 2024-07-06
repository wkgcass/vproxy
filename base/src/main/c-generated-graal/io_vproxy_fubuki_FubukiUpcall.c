#include "io_vproxy_fubuki_FubukiUpcall.h"
#include <stdio.h>
#include <stdlib.h>

#ifdef __cplusplus
extern "C" {
#endif

static void (*_onPacket)(void *,void *,int64_t,void *);
static void (*_addAddress)(void *,int32_t,int32_t,void *);
static void (*_deleteAddress)(void *,int32_t,int32_t,void *);

JNIEXPORT void JNICALL JavaCritical_io_vproxy_fubuki_FubukiUpcall_INIT(
    void (*onPacket)(void *,void *,int64_t,void *),
    void (*addAddress)(void *,int32_t,int32_t,void *),
    void (*deleteAddress)(void *,int32_t,int32_t,void *)
) {
    _onPacket = onPacket;
    _addAddress = addAddress;
    _deleteAddress = deleteAddress;
}

JNIEXPORT void JNICALL JavaCritical_io_vproxy_fubuki_FubukiUpcall_onPacket(void * packet, int64_t len, void * ctx) {
    if (_onPacket == NULL) {
        printf("JavaCritical_io_vproxy_fubuki_FubukiUpcall_onPacket function pointer is null");
        fflush(stdout);
        exit(1);
    }
    _onPacket(GetPNIGraalThread(), packet, len, ctx);
}

JNIEXPORT void JNICALL JavaCritical_io_vproxy_fubuki_FubukiUpcall_addAddress(int32_t addr, int32_t netmask, void * ctx) {
    if (_addAddress == NULL) {
        printf("JavaCritical_io_vproxy_fubuki_FubukiUpcall_addAddress function pointer is null");
        fflush(stdout);
        exit(1);
    }
    _addAddress(GetPNIGraalThread(), addr, netmask, ctx);
}

JNIEXPORT void JNICALL JavaCritical_io_vproxy_fubuki_FubukiUpcall_deleteAddress(int32_t addr, int32_t netmask, void * ctx) {
    if (_deleteAddress == NULL) {
        printf("JavaCritical_io_vproxy_fubuki_FubukiUpcall_deleteAddress function pointer is null");
        fflush(stdout);
        exit(1);
    }
    _deleteAddress(GetPNIGraalThread(), addr, netmask, ctx);
}

#ifdef __cplusplus
}
#endif
// metadata.generator-version: pni 22.0.0.17
// sha256:29add3852cf7bc079d164d4138581cd57e96b54f1746f36fc3492a06ee688740

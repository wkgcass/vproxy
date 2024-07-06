#include "io_vproxy_fubuki_FubukiUpcall.h"
#include <stdio.h>
#include <stdlib.h>

#ifdef __cplusplus
extern "C" {
#endif

static void (*_onPacket)(void *,int64_t,void *);
static void (*_addAddress)(int32_t,int32_t,void *);
static void (*_deleteAddress)(int32_t,int32_t,void *);

JNIEXPORT void JNICALL JavaCritical_io_vproxy_fubuki_FubukiUpcall_INIT(
    void (*onPacket)(void *,int64_t,void *),
    void (*addAddress)(int32_t,int32_t,void *),
    void (*deleteAddress)(int32_t,int32_t,void *)
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
    _onPacket(packet, len, ctx);
}

JNIEXPORT void JNICALL JavaCritical_io_vproxy_fubuki_FubukiUpcall_addAddress(int32_t addr, int32_t netmask, void * ctx) {
    if (_addAddress == NULL) {
        printf("JavaCritical_io_vproxy_fubuki_FubukiUpcall_addAddress function pointer is null");
        fflush(stdout);
        exit(1);
    }
    _addAddress(addr, netmask, ctx);
}

JNIEXPORT void JNICALL JavaCritical_io_vproxy_fubuki_FubukiUpcall_deleteAddress(int32_t addr, int32_t netmask, void * ctx) {
    if (_deleteAddress == NULL) {
        printf("JavaCritical_io_vproxy_fubuki_FubukiUpcall_deleteAddress function pointer is null");
        fflush(stdout);
        exit(1);
    }
    _deleteAddress(addr, netmask, ctx);
}

#ifdef __cplusplus
}
#endif
// metadata.generator-version: pni 22.0.0.17
// sha256:9451690ffe4eb15c347a0e71c0e4642bd722f829f0a81bacc85432d0859f91ad

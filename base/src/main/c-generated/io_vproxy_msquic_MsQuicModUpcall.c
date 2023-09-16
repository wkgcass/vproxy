#include "io_vproxy_msquic_MsQuicModUpcall.h"
#include <stdio.h>
#include <stdlib.h>

#ifdef __cplusplus
extern "C" {
#endif

static int32_t (*_dispatch)(void *,int32_t,void *);

JNIEXPORT void JNICALL JavaCritical_io_vproxy_msquic_MsQuicModUpcall_INIT(
    int32_t (*dispatch)(void *,int32_t,void *)
) {
    _dispatch = dispatch;
}

JNIEXPORT int32_t JNICALL JavaCritical_io_vproxy_msquic_MsQuicModUpcall_dispatch(void * worker, int32_t eventQ, void * thread) {
    if (_dispatch == NULL) {
        printf("JavaCritical_io_vproxy_msquic_MsQuicModUpcall_dispatch function pointer is null");
        fflush(stdout);
        exit(1);
    }
    return _dispatch(worker, eventQ, thread);
}

#ifdef __cplusplus
}
#endif
// metadata.generator-version: pni 21.0.0.8
// sha256:e00d65eecbe9a23e1e09fa5013aa53389e10d44ce42d8e682c705621108c8464

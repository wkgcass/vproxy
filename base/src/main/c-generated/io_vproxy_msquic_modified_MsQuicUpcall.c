#include "io_vproxy_msquic_modified_MsQuicUpcall.h"
#include <stdio.h>
#include <stdlib.h>

#ifdef __cplusplus
extern "C" {
#endif

static int32_t (*_dispatch)(void *,int32_t,void *);

JNIEXPORT void JNICALL JavaCritical_io_vproxy_msquic_modified_MsQuicUpcall_INIT(
    int32_t (*dispatch)(void *,int32_t,void *)
) {
    _dispatch = dispatch;
}

JNIEXPORT int32_t JNICALL JavaCritical_io_vproxy_msquic_modified_MsQuicUpcall_dispatch(void * worker, int32_t eventQ, void * thread) {
    if (_dispatch == NULL) {
        printf("JavaCritical_io_vproxy_msquic_modified_MsQuicUpcall_dispatch function pointer is null");
        fflush(stdout);
        exit(1);
    }
    return _dispatch(worker, eventQ, thread);
}

#ifdef __cplusplus
}
#endif
// metadata.generator-version: pni 21.0.0.8
// sha256:bf0b21b4dc0466c24b464a7b4e18fda89110fe6f455267fe3c0dfe6c8951945b

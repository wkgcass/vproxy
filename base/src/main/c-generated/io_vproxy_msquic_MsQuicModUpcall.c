#include "io_vproxy_msquic_MsQuicModUpcall.h"
#include <stdio.h>
#include <stdlib.h>

#ifdef __cplusplus
extern "C" {
#endif

static int32_t (*_dispatch)(void *,int32_t,void *,void *);

JNIEXPORT void JNICALL JavaCritical_io_vproxy_msquic_MsQuicModUpcall_INIT(
    int32_t (*dispatch)(void *,int32_t,void *,void *)
) {
    _dispatch = dispatch;
}

JNIEXPORT int32_t JNICALL JavaCritical_io_vproxy_msquic_MsQuicModUpcall_dispatch(void * worker, int32_t epfd, void * thread, void * context) {
    if (_dispatch == NULL) {
        printf("JavaCritical_io_vproxy_msquic_MsQuicModUpcall_dispatch function pointer is null");
        fflush(stdout);
        exit(1);
    }
    return _dispatch(worker, epfd, thread, context);
}

#ifdef __cplusplus
}
#endif
// metadata.generator-version: pni 21.0.0.8
// sha256:f29391ebebf79a9a130e259507685f6f07f746793c07ff6876f3dd7de4ae00f1

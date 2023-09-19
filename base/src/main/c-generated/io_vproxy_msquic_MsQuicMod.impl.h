#include "io_vproxy_msquic_MsQuicMod.h"
#include <stdio.h>

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT void JNICALL JavaCritical_io_vproxy_msquic_MsQuicMod_MsQuicCxPlatWorkerThreadInit(struct CxPlatProcessEventLocals * CxPlatWorkerThreadLocals) {
    MsQuicCxPlatWorkerThreadInit(CxPlatWorkerThreadLocals);
}

JNIEXPORT void JNICALL JavaCritical_io_vproxy_msquic_MsQuicMod_MsQuicCxPlatWorkerThreadBeforePoll(struct CxPlatProcessEventLocals * CxPlatProcessEventLocals) {
    MsQuicCxPlatWorkerThreadBeforePoll(CxPlatProcessEventLocals);
}

JNIEXPORT uint8_t JNICALL JavaCritical_io_vproxy_msquic_MsQuicMod_MsQuicCxPlatWorkerThreadAfterPoll(struct CxPlatProcessEventLocals * locals, int32_t num, aeFiredExtra * events) {
    locals->CqeCount = num;
    for (int i = 0; i < num && i < CxPlatProcessCqesArraySize; ++i) {
    #ifdef __linux__
        locals->Cqes[i].data.ptr = events[i].ud;
        locals->Cqes[i].events = events[i].mask;
    #elif defined(__APPLE__)
        locals->Cqes[i].udata = events[i].ud;
        locals->Cqes[i].filter = events[i].mask;
    #else
        locals->CqeCount = 0;
        printf("unsupported platform\n");
        fflush(stdout);
    #endif
    }
    int ret = MsQuicCxPlatWorkerThreadAfterPoll(locals);
    return ret;
}

JNIEXPORT int32_t JNICALL JavaCritical_io_vproxy_msquic_MsQuicMod_MsQuicCxPlatWorkerThreadFinalize(struct CxPlatProcessEventLocals * CxPlatWorkerThreadLocals) {
    return MsQuicCxPlatWorkerThreadFinalize(CxPlatWorkerThreadLocals);
}

JNIEXPORT int32_t JNICALL JavaCritical_io_vproxy_msquic_MsQuicMod_MsQuicSetEventLoopThreadDispatcher(void) {
    return MsQuicSetEventLoopThreadDispatcher(vproxy_MsQuicUpcall_dispatch);
}

JNIEXPORT int32_t JNICALL JavaCritical_io_vproxy_msquic_MsQuicMod_CxPlatGetCurThread(void * Thread) {
    return CxPlatGetCurThread((CXPLAT_THREAD*) Thread);
}

#ifdef __cplusplus
}
#endif
// metadata.generator-version: pni 21.0.0.11
// sha256:7cf666e96039b0267276a7b4f995f7245fb846e443c8c57cc9549a03694a0b3a

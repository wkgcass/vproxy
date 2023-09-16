#include "io_vproxy_msquic_MsQuicMod.h"
#include <stdio.h>

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT int64_t JNICALL JavaCritical_io_vproxy_msquic_MsQuicMod_getCxPlatProcessEventLocalsMemorySize(void) {
    return sizeof(struct CxPlatProcessEventLocals);
}

JNIEXPORT int64_t JNICALL JavaCritical_io_vproxy_msquic_MsQuicMod_getCXPLAT_EXECUTION_STATEMemorySize(void) {
    return sizeof(CXPLAT_EXECUTION_STATE);
}

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
// metadata.generator-version: pni 21.0.0.8
// sha256:cdeccfd56cb8c721e4c54b0443599d73673c2d63b7964facdd39a66404407b2e

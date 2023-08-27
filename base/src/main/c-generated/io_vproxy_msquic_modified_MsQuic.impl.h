#include "io_vproxy_msquic_modified_MsQuic.h"
#include <stdio.h>

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT int64_t JNICALL JavaCritical_io_vproxy_msquic_modified_MsQuic_getCxPlatProcessEventLocalsMemorySize(void) {
    return sizeof(struct CxPlatProcessEventLocals);
}

JNIEXPORT int64_t JNICALL JavaCritical_io_vproxy_msquic_modified_MsQuic_getCXPLAT_EXECUTION_STATEMemorySize(void) {
    return sizeof(CXPLAT_EXECUTION_STATE);
}

JNIEXPORT void JNICALL JavaCritical_io_vproxy_msquic_modified_MsQuic_MsQuicCxPlatWorkerThreadInit(struct CxPlatProcessEventLocals * CxPlatWorkerThreadLocals) {
    MsQuicCxPlatWorkerThreadInit(CxPlatWorkerThreadLocals);
}

JNIEXPORT void JNICALL JavaCritical_io_vproxy_msquic_modified_MsQuic_MsQuicCxPlatWorkerThreadBeforePoll(struct CxPlatProcessEventLocals * CxPlatProcessEventLocals) {
    MsQuicCxPlatWorkerThreadBeforePoll(CxPlatProcessEventLocals);
}

JNIEXPORT uint8_t JNICALL JavaCritical_io_vproxy_msquic_modified_MsQuic_MsQuicCxPlatWorkerThreadAfterPoll(struct CxPlatProcessEventLocals * locals, int32_t num, aeFiredExtra * events) {
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

JNIEXPORT int32_t JNICALL JavaCritical_io_vproxy_msquic_modified_MsQuic_MsQuicCxPlatWorkerThreadFinalize(struct CxPlatProcessEventLocals * CxPlatWorkerThreadLocals) {
    return MsQuicCxPlatWorkerThreadFinalize(CxPlatWorkerThreadLocals);
}

JNIEXPORT int32_t JNICALL JavaCritical_io_vproxy_msquic_modified_MsQuic_MsQuicSetEventLoopThreadDispatcher(void) {
    return MsQuicSetEventLoopThreadDispatcher(vproxy_MsQuicUpcall_dispatch);
}

JNIEXPORT int32_t JNICALL JavaCritical_io_vproxy_msquic_modified_MsQuic_CxPlatGetCurThread(void * Thread) {
    return CxPlatGetCurThread((CXPLAT_THREAD*) Thread);
}

#ifdef __cplusplus
}
#endif
// metadata.generator-version: pni 21.0.0.8
// sha256:5464b326e2b4c6b57f2209c7d9c9a599049b45fa60ce6b39edd99f72fb029838

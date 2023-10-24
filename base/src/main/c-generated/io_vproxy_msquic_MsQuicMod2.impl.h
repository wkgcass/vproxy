#include "io_vproxy_msquic_MsQuicMod2.h"
#include <stdio.h>

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT void JNICALL JavaCritical_io_vproxy_msquic_MsQuicMod2_MsQuicCxPlatWorkerThreadInit(struct CxPlatProcessEventLocals * CxPlatWorkerThreadLocals) {
    MsQuicCxPlatWorkerThreadInit(CxPlatWorkerThreadLocals);
}

JNIEXPORT void JNICALL JavaCritical_io_vproxy_msquic_MsQuicMod2_MsQuicCxPlatWorkerThreadBeforePoll(struct CxPlatProcessEventLocals * CxPlatProcessEventLocals) {
    MsQuicCxPlatWorkerThreadBeforePoll(CxPlatProcessEventLocals);
}

JNIEXPORT uint8_t JNICALL JavaCritical_io_vproxy_msquic_MsQuicMod2_MsQuicCxPlatWorkerThreadAfterPoll(struct CxPlatProcessEventLocals * locals, int32_t num, aeFiredExtra * events) {
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

JNIEXPORT int32_t JNICALL JavaCritical_io_vproxy_msquic_MsQuicMod2_MsQuicCxPlatWorkerThreadFinalize(struct CxPlatProcessEventLocals * CxPlatWorkerThreadLocals) {
    return MsQuicCxPlatWorkerThreadFinalize(CxPlatWorkerThreadLocals);
}

#ifdef __cplusplus
}
#endif
// metadata.generator-version: pni 21.0.0.14
// sha256:6549fdff39e4231b3a3a119bf2d43235cf4b858c232cc06445c16dcc3174c06e

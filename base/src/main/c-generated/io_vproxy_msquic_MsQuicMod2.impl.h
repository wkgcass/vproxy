#include "io_vproxy_msquic_MsQuicMod2.h"

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
    #else // windows
        locals->Cqes[i].lpOverlapped = events[i].ud;
        locals->Cqes[i].dwNumberOfBytesTransferred = events[i].mask;
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
// metadata.generator-version: pni 22.0.0.17
// sha256:d415bf48163e676b954201e489e7a8eb0b17af1022578e647599f68dd6ec71f0

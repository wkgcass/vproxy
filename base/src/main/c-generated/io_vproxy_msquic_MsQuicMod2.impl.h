#include "io_vproxy_msquic_MsQuicMod2.h"

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT void JNICALL JavaCritical_io_vproxy_msquic_MsQuicMod2_WorkerThreadInit(QUIC_EXTRA_API_TABLE * api, struct CxPlatProcessEventLocals * CxPlatWorkerThreadLocals) {
    api->WorkerThreadInit(CxPlatWorkerThreadLocals);
}

JNIEXPORT void JNICALL JavaCritical_io_vproxy_msquic_MsQuicMod2_WorkerThreadBeforePoll(QUIC_EXTRA_API_TABLE * api, struct CxPlatProcessEventLocals * CxPlatProcessEventLocals) {
    api->WorkerThreadBeforePoll(CxPlatProcessEventLocals);
}

JNIEXPORT uint8_t JNICALL JavaCritical_io_vproxy_msquic_MsQuicMod2_WorkerThreadAfterPoll(QUIC_EXTRA_API_TABLE * api, struct CxPlatProcessEventLocals * locals, int32_t num, aeFiredExtra * events) {
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
    int ret = api->WorkerThreadAfterPoll(locals);
    return ret;
}

JNIEXPORT int32_t JNICALL JavaCritical_io_vproxy_msquic_MsQuicMod2_WorkerThreadFinalize(QUIC_EXTRA_API_TABLE * api, struct CxPlatProcessEventLocals * CxPlatWorkerThreadLocals) {
    return api->WorkerThreadFinalize(CxPlatWorkerThreadLocals);
}

#ifdef __cplusplus
}
#endif
// metadata.generator-version: pni 22.0.0.17
// sha256:64b3263dffa56295007578ca4b4d9c65f902a6fb86dc6b406776f85755b93825

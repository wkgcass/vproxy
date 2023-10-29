#include "pni.h"

#if PNI_GRAAL

static __thread void* _graalThread;
static void* _graalIsolate;

JNIEXPORT void JNICALL SetPNIGraalThread(void* thread) {
    _graalThread = thread;
}

JNIEXPORT void* JNICALL GetPNIGraalThread(void) {
    return _graalThread;
}

JNIEXPORT void JNICALL SetPNIGraalIsolate(void* isolate) {
    _graalIsolate = isolate;
}

JNIEXPORT void* JNICALL GetPNIGraalIsolate(void) {
    return _graalIsolate;
}

#endif // PNI_GRAAL

static PNIFuncInvokeFunc _PNIFuncInvokeFunc;

JNIEXPORT PNIFuncInvokeFunc JNICALL GetPNIFuncInvokeFunc(void) {
    return _PNIFuncInvokeFunc;
}
JNIEXPORT void JNICALL SetPNIFuncInvokeFunc(PNIFuncInvokeFunc f) {
    _PNIFuncInvokeFunc = f;
}

static PNIFuncReleaseFunc _PNIFuncReleaseFunc;

JNIEXPORT PNIFuncReleaseFunc JNICALL GetPNIFuncReleaseFunc(void) {
    return _PNIFuncReleaseFunc;
}
JNIEXPORT void JNICALL SetPNIFuncReleaseFunc(PNIFuncReleaseFunc f) {
    _PNIFuncReleaseFunc = f;
}

static PNIRefReleaseFunc _PNIRefReleaseFunc;

JNIEXPORT PNIRefReleaseFunc JNICALL GetPNIRefReleaseFunc(void) {
    return _PNIRefReleaseFunc;
}
JNIEXPORT void JNICALL SetPNIRefReleaseFunc(PNIRefReleaseFunc f) {
    _PNIRefReleaseFunc = f;
}

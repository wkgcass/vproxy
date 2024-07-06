#ifndef PNIENV_H
#define PNIENV_H

#include <jni.h>
#include <inttypes.h>
#include <string.h>
#include <errno.h>

#if defined(__GNUC__)
  #define PNI_PACK( __t__, __n__, __Declaration__ ) __t__ __n__ __Declaration__ __attribute__((__packed__))
#else
  #define PNI_PACK( __t__, __n__, __Declaration__ ) __pragma(pack(push, 1)) __t__ __n__ __Declaration__ __pragma(pack(pop))
#endif

typedef struct PNIException {
    char* type;
#define PNIExceptionMessageLen (4096)
    char  message[PNIExceptionMessageLen];
    int32_t errno_; /* padding uint32_t : 32; */
} PNIException;

typedef struct PNIBuf {
    void*    buf;
    uint64_t len;
} PNIBuf;

typedef struct PNIEnv {
    PNIException ex;
    union {
        int8_t   return_byte;
        uint16_t return_char;
        double   return_double;
        int32_t  return_int;
        float    return_float;
        int64_t  return_long;
        int16_t  return_short;
        int8_t   return_bool;
        void*    return_pointer;
        PNIBuf   return_buf;
    };
} PNIEnv;

typedef struct PNIEnvUnionPlaceHolder {
    uint64_t : 64;
    uint64_t : 64;
} PNIEnvUnionPlaceHolder;

#define PNIEnvExpand(EnvType, ValueType) \
typedef struct PNIEnv_##EnvType { \
    PNIException ex; \
    union { \
        ValueType return_; \
        PNIEnvUnionPlaceHolder __placeholder__; \
    }; \
} PNIEnv_##EnvType;
// end #define PNIEnvExpand

PNIEnvExpand(byte, int8_t)
PNIEnvExpand(char, uint16_t)
PNIEnvExpand(float, float)
PNIEnvExpand(double, double)
PNIEnvExpand(int, int32_t)
PNIEnvExpand(long, int64_t)
PNIEnvExpand(short, int16_t)
PNIEnvExpand(bool, uint8_t)
PNIEnvExpand(pointer, void*)
PNIEnvExpand(string, char*)
PNIEnvExpand(buf, PNIBuf)

typedef struct PNIEnv_void {
    PNIException ex;
    PNIEnvUnionPlaceHolder __placeholder__;
} PNIEnv_void;

static inline int PNIThrowException(void* _env, const char* extype, char* message) {
    PNIEnv* env = _env;
    env->ex.type = (char*) extype;
    strncpy(env->ex.message, message, PNIExceptionMessageLen);
    env->ex.message[PNIExceptionMessageLen - 1] = '\0';
    return -1;
}

static inline int PNIThrowExceptionBasedOnErrno(void* _env, const char* extype) {
    return PNIThrowException(_env, extype, strerror(errno));
}

static inline void PNIStoreErrno(void* _env) {
    PNIEnv* env = _env;
    env->ex.errno_ = errno;
}

#if PNI_GRAAL
JNIEXPORT void  JNICALL SetPNIGraalThread(void* thread);
JNIEXPORT void* JNICALL GetPNIGraalThread(void);
JNIEXPORT void  JNICALL SetPNIGraalIsolate(void* isolate);
JNIEXPORT void* JNICALL GetPNIGraalIsolate(void);
#endif // PNI_GRAAL

typedef struct PNIFunc {
    int64_t   index;
    union {
        void*    userdata;
        uint64_t udata64;
    };
} PNIFunc;

PNIEnvExpand(func, PNIFunc*)

#define PNIFuncInvokeExceptionCaught ((int32_t) 0x800000f1)
#define PNIFuncInvokeNoSuchFunction  ((int32_t) 0x800000f2)

#if PNI_GRAAL
typedef int32_t (*PNIFuncInvokeFunc)(void*,int64_t,void*);
#else
typedef int32_t (*PNIFuncInvokeFunc)(int64_t,void*);
#endif // PNI_GRAAL
JNIEXPORT PNIFuncInvokeFunc JNICALL GetPNIFuncInvokeFunc(void);
JNIEXPORT void JNICALL SetPNIFuncInvokeFunc(PNIFuncInvokeFunc f);

static inline int32_t PNIFuncInvoke(PNIFunc* f, void* data) {
#if PNI_GRAAL
    return GetPNIFuncInvokeFunc()(GetPNIGraalThread(), f->index, data);
#else
    return GetPNIFuncInvokeFunc()(f->index, data);
#endif // PNI_GRAAL
}

#if PNI_GRAAL
typedef void (*PNIFuncReleaseFunc)(void*,int64_t);
#else
typedef void (*PNIFuncReleaseFunc)(int64_t);
#endif // PNI_GRAAL
JNIEXPORT PNIFuncReleaseFunc JNICALL GetPNIFuncReleaseFunc(void);
JNIEXPORT void JNICALL SetPNIFuncReleaseFunc(PNIFuncReleaseFunc f);

static inline void PNIFuncRelease(PNIFunc* f) {
#if PNI_GRAAL
    GetPNIFuncReleaseFunc()(GetPNIGraalThread(), f->index);
#else
    GetPNIFuncReleaseFunc()(f->index);
#endif // PNI_GRAAL
}

typedef struct PNIRef {
    int64_t index;
    union {
        void*    userdata;
        uint64_t udata64;
    };
} PNIRef;

PNIEnvExpand(ref, PNIRef*)

#if PNI_GRAAL
typedef void (*PNIRefReleaseFunc)(void*,int64_t);
#else
typedef void (*PNIRefReleaseFunc)(int64_t);
#endif // PNI_GRAAL
JNIEXPORT PNIRefReleaseFunc JNICALL GetPNIRefReleaseFunc(void);
JNIEXPORT void JNICALL SetPNIRefReleaseFunc(PNIRefReleaseFunc f);

static inline void PNIRefRelease(PNIRef* ref) {
#if PNI_GRAAL
    GetPNIRefReleaseFunc()(GetPNIGraalThread(), ref->index);
#else
    GetPNIRefReleaseFunc()(ref->index);
#endif // PNI_GRAAL
}

#define PNIBufExpand(BufType, ValueType, Size) \
typedef struct PNIBuf_##BufType { \
    union { \
        ValueType* array; \
        void*      buf; \
    }; \
    uint64_t bufLen; \
} PNIBuf_##BufType; \
static inline uint64_t BufType##PNIArrayLen(PNIBuf_##BufType* buf) { \
    return buf->bufLen / (Size == 0 ? 1 : Size); \
} \
static inline uint64_t BufType##PNIBufLen(uint64_t arrayLen) { \
    return arrayLen * Size; \
} \
PNIEnvExpand(buf_##BufType, PNIBuf_##BufType)
// end #define PNIBufExpand

PNIBufExpand(byte, int8_t, 1)
PNIBufExpand(ubyte, uint8_t, 1)
PNIBufExpand(char, uint16_t, 2)
PNIBufExpand(int, int32_t, 4)
PNIBufExpand(uint, uint32_t, 4)
PNIBufExpand(long, int64_t, 8)
PNIBufExpand(ulong, uint64_t, 8)
PNIBufExpand(float, float, 4)
PNIBufExpand(double, double, 8)
PNIBufExpand(short, int16_t, 2)
PNIBufExpand(ushort, uint16_t, 2)
PNIBufExpand(bool, uint8_t, 1)
PNIBufExpand(ptr, void *, 8)

#endif // PNIENV_H

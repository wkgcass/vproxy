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

typedef PNI_PACK(struct, PNIException, {
    char* type;
#define PNIExceptionMessageLen (4096)
    char  message[PNIExceptionMessageLen];
    int32_t errno_; /* padding */ uint64_t :32;
}) PNIException;

typedef PNI_PACK(struct, PNIBuf, {
    void*    buf;
    uint64_t len;
}) PNIBuf;

typedef PNI_PACK(struct, PNIEnv, {
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
}) PNIEnv;

typedef PNI_PACK(struct, PNIEnvUnionPlaceHolder, {
    uint64_t : 64;
    uint64_t : 64;
}) PNIEnvUnionPlaceHolder;

#define PNIEnvExpand(EnvType, ValueType) \
typedef PNI_PACK(struct, PNIEnv_##EnvType, { \
    PNIException ex; \
    union { \
        ValueType return_; \
        PNIEnvUnionPlaceHolder __placeholder__; \
    }; \
}) PNIEnv_##EnvType;
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

typedef PNI_PACK(struct, PNIEnv_void, {
    PNIException ex;
    PNIEnvUnionPlaceHolder __placeholder__;
}) PNIEnv_void;

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

typedef PNI_PACK(struct, PNIFunc, {
    int64_t   index;
    int32_t (*func)(int64_t,void*);
    void    (*release)(int64_t);

    union {
        void*    userdata;
        uint64_t udata64;
    };
}) PNIFunc;

PNIEnvExpand(func, PNIFunc*)

#define PNIFuncInvokeExceptionCaught ((int32_t) 0x800000f1)
#define PNIFuncInvokeNoSuchFunction  ((int32_t) 0x800000f2)

static inline int PNIFuncInvoke(PNIFunc* f, void* data) {
    return f->func(f->index, data);
}

static inline void PNIFuncRelease(PNIFunc* f) {
    f->release(f->index);
}

typedef PNI_PACK(struct, PNIRef, {
    int64_t index;
    void  (*release)(int64_t);

    union {
        void*    userdata;
        uint64_t udata64;
    };
}) PNIRef;

PNIEnvExpand(ref, PNIRef*)

static inline void PNIRefRelease(PNIRef* ref) {
    ref->release(ref->index);
}

#define PNIBufExpand(BufType, ValueType, Size) \
typedef PNI_PACK(struct, PNIBuf_##BufType, { \
    union { \
        ValueType* array; \
        void*      buf; \
    }; \
    uint64_t bufLen; \
}) PNIBuf_##BufType; \
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

#endif // PNIENV_H

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
typedef struct { \
    uint64_t  (*len)(PNIBuf*); \
    ValueType (*get)(PNIBuf*,uint64_t); \
    void      (*set)(PNIBuf*,uint64_t, ValueType ); \
\
    size_t    (*byteSize)(uint64_t); \
    void      (*setIntoBuf)(PNIBuf*,ValueType*,uint64_t); \
} PNI##BufType##BufHandle; \
static PNI##BufType##BufHandle __pni##BufType##BufHandle; \
static uint64_t PNI##BufType##BufLen(PNIBuf* buf) { \
    return buf->len / sizeof(ValueType); \
} \
static ValueType PNI##BufType##BufGet(PNIBuf* buf, uint64_t index) { \
    ValueType * b = buf->buf; \
    return b[index]; \
} \
static void PNI##BufType##BufSet(PNIBuf* buf, uint64_t index, ValueType value) { \
    ValueType * b = buf->buf; \
    b[index] = value; \
} \
static size_t PNI##BufType##ByteSize(uint64_t len) { \
    return Size * len; \
} \
static void PNI##BufType##SetIntoBuf(PNIBuf* buf, ValueType* arr, uint64_t len) { \
    buf->buf = (void*) arr; \
    buf->len = Size * len; \
} \
static inline PNI##BufType##BufHandle* GetPNI##BufType##BufHandle() { \
    __pni##BufType##BufHandle.len = PNI##BufType##BufLen; \
    __pni##BufType##BufHandle.get = PNI##BufType##BufGet; \
    __pni##BufType##BufHandle.set = PNI##BufType##BufSet; \
    __pni##BufType##BufHandle.byteSize = PNI##BufType##ByteSize; \
    __pni##BufType##BufHandle.setIntoBuf = PNI##BufType##SetIntoBuf; \
    return &__pni##BufType##BufHandle; \
}
// end #define PNIBufExpand

PNIBufExpand(Byte, int8_t, 1)
PNIBufExpand(Char, uint16_t, 2)
PNIBufExpand(Int, int32_t, 4)
PNIBufExpand(Long, int64_t, 8)
PNIBufExpand(Float, float, 4)
PNIBufExpand(Double, double, 8)
PNIBufExpand(Short, int16_t, 2)
PNIBufExpand(Bool, uint8_t, 1)

static inline void PNIBufSetToNull(PNIBuf* buf) {
    buf->len = 0;
    buf->buf = NULL;
}

#endif // PNIENV_H

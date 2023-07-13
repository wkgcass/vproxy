#ifndef VPROXY_ENV_H
#define VPROXY_ENV_H

#include <jni.h>
#include <inttypes.h>
#include <string.h>
#include <errno.h>

#ifdef __linux__
void strlcpy(char *dst, const char *src, size_t size) {
    strncpy(dst, src, size);
    dst[size - 1] = '\0';
}
#endif

#define EXCEPTION_TYPE_UnsupportedOperationException (1)
#define EXCEPTION_TYPE_IOException                   (2)

typedef struct {
    uint32_t type;
    char message[128];
} __attribute__((packed)) exception_st;

typedef struct {
    exception_st ex;
    union {
        int32_t return_i;
        int64_t return_j;
        int8_t  return_z;
        void*   return_p;
    };
} __attribute__((packed)) JEnv;

static inline void JEnvThrowNew(JEnv* env, int extype, char* message) {
    env->ex.type = extype;
    strlcpy(env->ex.message, message, 128);
}

static inline void throwUnsupportedOperationException(JEnv* env, char* message) {
    int UnsupportedOperationException = EXCEPTION_TYPE_UnsupportedOperationException;
    JEnvThrowNew(env, UnsupportedOperationException, message);
}

static inline void throwIOException(JEnv* env, char* message) {
    int IOException = EXCEPTION_TYPE_IOException;
    JEnvThrowNew(env, IOException, message);
}

static inline void throwIOExceptionBasedOnErrno(JEnv* env) {
    char* msg = strerror(errno);
    throwIOException(env, msg);
}

#ifdef _WIN32
#include <windows.h>

static inline void throwIOExceptionBasedOnLastError(JEnv* env, char* msgPrefix) {
    char errMsg[128];
    sprintf(errMsg, "%s: %d", msgPrefix, GetLastError());
    throwIOException(env, errMsg);
}
#endif

#endif

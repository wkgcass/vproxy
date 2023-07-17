#ifndef VPROXY_ENV_H
#define VPROXY_ENV_H

#include <jni.h>
#include <inttypes.h>
#include <string.h>
#include <errno.h>

#if defined(__linux__) || defined(WIN32)
void strlcpy(char *dst, const char *src, size_t size) {
    strncpy(dst, src, size);
    dst[size - 1] = '\0';
}
#endif

typedef struct {
    char* type;
    char  message[4096];
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

static inline int JEnvThrowNew(JEnv* env, const char* extype, char* message) {
    env->ex.type = (char*) extype;
    strlcpy(env->ex.message, message, 4096);
    return -1;
}

static inline int throwUnsupportedOperationException(JEnv* env, char* message) {
    return JEnvThrowNew(env, "java.lang.UnsupportedOperationException", message);
}

static inline int throwIOException(JEnv* env, char* message) {
    return JEnvThrowNew(env, "java.io.IOException", message);
}

static inline int throwIOExceptionBasedOnErrno(JEnv* env) {
    char* msg = strerror(errno);
    return throwIOException(env, msg);
}

#if defined(WIN32)
#include <windows.h>

static inline int throwIOExceptionBasedOnLastError(JEnv* env, char* msgPrefix) {
    char errMsg[4096];
    sprintf(errMsg, "%s: %d", msgPrefix, GetLastError());
    return throwIOException(env, errMsg);
}
#endif

#endif

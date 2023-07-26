#ifndef VPROXY_ENV_H
#define VPROXY_ENV_H

#include <pni.h>

#if defined(__linux__) || defined(WIN32)
void strlcpy(char *dst, const char *src, size_t size) {
    strncpy(dst, src, size);
    dst[size - 1] = '\0';
}
#endif

static inline int throwUnsupportedOperationException(void* env, char* message) {
    return PNIThrowException(env, "java.lang.UnsupportedOperationException", message);
}

static inline int throwIOException(void* env, char* message) {
    return PNIThrowException(env, "java.io.IOException", message);
}

static inline int throwIOExceptionBasedOnErrno(void* env) {
    char* msg = strerror(errno);
    return throwIOException(env, msg);
}

#if defined(WIN32)
#include <windows.h>

static inline int throwIOExceptionBasedOnLastError(void* env, char* msgPrefix) {
    char errMsg[4096];
    sprintf(errMsg, "%s: %d", msgPrefix, GetLastError());
    return throwIOException(env, errMsg);
}
#endif

#endif

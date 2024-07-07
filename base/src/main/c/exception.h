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

#ifndef _WIN32
static inline int throwIOExceptionBasedOnErrno(void* env) {
    char* msg = strerror(errno);
    return throwIOException(env, msg);
}
#else

#include <errhandlingapi.h>

static inline int throwIOExceptionBasedOnErrno(void* _env) {
    PNIEnv* env = _env;
    int err = GetLastError();
    env->ex.type = "java.io.IOException";
    strncpy(env->ex.message, message, PNIExceptionMessageLen);
    FormatMessage(FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS,
                  0, err, 0, env->ex.message, PNIExceptionMessageLen, 0);
    return -1;
}

static inline int throwIOExceptionBasedOnErrnoWithPrefix(void* _env, char* msgPrefix) {
    PNIEnv* env = _env;
    int err = GetLastError();
    env->ex.type = "java.io.IOException";
    int prefixLen = strlen(msgPrefix);
    strncpy(env->ex.message, msgPrefix, PNIExceptionMessageLen);
    int extraChars = 0;
    env->ex.message[prefixLen + (extraChars++)] = ':';
    env->ex.message[prefixLen + (extraChars++)] = ' ';
    FormatMessage(FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS,
                  0, err, 0,
                  env->ex.message + prefixLen + extraChars,
                  PNIExceptionMessageLen - prefixLen - extraChars,
                  0);
    return -1;
}
#endif // _WIN32

#endif // VPROXY_ENV_H

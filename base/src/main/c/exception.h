#ifndef VPROXY_ENV_H
#define VPROXY_ENV_H

#include <pni.h>

#if defined(_WIN32)
#define VPROXY_STRLCPY_COMPAT
#elif defined(__linux__) && defined(__GLIBC__)
// Function-like macros are expanded even in a false && expression.
// Keep the glibc version check inside its own platform branch.
#if !__GLIBC_PREREQ(2, 38)
#define VPROXY_STRLCPY_COMPAT
#endif
#endif

#ifdef VPROXY_STRLCPY_COMPAT
static inline void strlcpy(char *dst, const char *src, size_t size) {
    strncpy(dst, src, size);
    dst[size - 1] = '\0';
}
#undef VPROXY_STRLCPY_COMPAT
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
    FormatMessage(FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS,
                  0, err, 0x0409 /* en-US */, env->ex.message, PNIExceptionMessageLen, 0);
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
                  0, err, 0x0409 /* en-US */,
                  env->ex.message + prefixLen + extraChars,
                  PNIExceptionMessageLen - prefixLen - extraChars,
                  0);
    return -1;
}
#endif // _WIN32

#endif // VPROXY_ENV_H

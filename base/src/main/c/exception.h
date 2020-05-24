#ifndef V_EXCEPTION
#define V_EXCEPTION 1

#include <jni.h>
#include <errno.h>
#include <string.h>

#include <assert.h>

void throwException(JNIEnv* env, char* className, char* message) {
    jclass exClass = (*env)->FindClass(env, className);
    assert(exClass != NULL);
    (*env)->ThrowNew(env, exClass, message);
}

void throwUnsupportedOperationException(JNIEnv* env, char* message) {
    throwException(env, "java/lang/UnsupportedOperationException", message);
}

void throwIOException(JNIEnv* env, char* message) {
    throwException(env, "java/io/IOException", message);
}

void throwIOExceptionBasedOnErrno(JNIEnv* env) {
    char* msg = strerror(errno);
    throwIOException(env, msg);
}

    #ifdef _WIN32
        #include <windows.h>

        void throwIOExceptionBasedOnLastError(JNIEnv* env, char* msgPrefix) {
            char errMsg[128];
            sprintf(errMsg, "%s: %d", msgPrefix, GetLastError());
            throwIOException(env, errMsg);
        }
    #endif

#endif

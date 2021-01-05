#ifndef V_EXCEPTION
#define V_EXCEPTION 1

#include <jni.h>
#include <errno.h>
#include <string.h>

jclass UnsupportedOperationException;

void throwUnsupportedOperationException(JNIEnv* env, char* message) {
    if (UnsupportedOperationException == NULL) {
        jclass exClass = (*env)->FindClass(env, "java/lang/UnsupportedOperationException");
        UnsupportedOperationException = (jclass)(*env)->NewGlobalRef(env, (jobject)exClass);
    }
    (*env)->ThrowNew(env, UnsupportedOperationException, message);
}

jclass IOException;

void throwIOException(JNIEnv* env, char* message) {
    if (IOException == NULL) {
        jclass exClass = (*env)->FindClass(env, "java/io/IOException");
        IOException = (jclass)(*env)->NewGlobalRef(env, (jobject)exClass);
    }
    (*env)->ThrowNew(env, IOException, message);
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

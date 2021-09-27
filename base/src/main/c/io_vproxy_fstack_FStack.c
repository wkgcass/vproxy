#include "io_vproxy_fstack_FStack.h"
#include <stdint.h>
#include <ff_api.h>

JNIEXPORT void JNICALL Java_io_vproxy_fstack_FStack_ff_1init0
  (JNIEnv* env, jobject self, jobjectArray args) {
    int i;
    int len = (*env)->GetArrayLength(env, args);
    char* charss[len];
    for (i = 0; i < len; ++i) {
        jstring s = (jstring) ((*env)->GetObjectArrayElement(env, args, i));
        const char* raw = (*env)->GetStringUTFChars(env, s, 0);
        charss[i] = (char*) raw;
    }
    ff_init(len, charss);
    // release ref
    for (i = 0; i < len; ++i) {
        (*env)->ReleaseStringUTFChars(env, (jstring) ((*env)->GetObjectArrayElement(env, args, i)), charss[i]);
    }
}

typedef struct {
    JNIEnv*   env;
    jclass    cls;
    jmethodID meth;
    jobject   runnable;
} loop_args;

int loop(void* argsx) {
    loop_args* args = (loop_args*) argsx;

    JNIEnv* env = args->env;
    jclass cls = args->cls;
    jmethodID meth = args->meth;
    jobject runnable = args->runnable;

    (*env)->CallVoidMethod(env, runnable, meth);

    return 0;
}

JNIEXPORT void JNICALL Java_io_vproxy_fstack_FStack_ff_1run0
  (JNIEnv* env, jobject self, jobject runnable) {
    jclass cls = (*env)->FindClass(env, "java/lang/Runnable");
    jmethodID meth = (*env)->GetMethodID(env, cls, "run", "()V");
    loop_args args;

    args.env = env;
    args.cls = cls;
    args.meth = meth;
    args.runnable = runnable;

    ff_run(loop, &args);
}

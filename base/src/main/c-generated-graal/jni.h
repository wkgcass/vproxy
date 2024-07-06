#ifndef PNI_JNIMOCK_H
#define PNI_JNIMOCK_H

#include <stdio.h>
#include <stdarg.h>

#ifdef WIN32
#define JNIEXPORT __declspec(dllexport)
#else
#define JNIEXPORT __attribute__((visibility("default")))
#endif

#ifdef WIN32
#define JNICALL __stdcall
#else
#define JNICALL
#endif

#include <inttypes.h>

typedef int8_t   jbyte;
typedef uint16_t jchar;
typedef double   jdouble;
typedef float    jfloat;
typedef int32_t  jint;
typedef int64_t  jlong;
typedef int16_t  jshort;
typedef uint8_t  jboolean;

#define JNI_FALSE (0)
#define JNI_TRUE  (1)

#define JNI_OK  (0)
#define JNI_ERR (-1)

#endif // PNI_JNIMOCK_H

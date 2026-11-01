#include <jni.h>
#include <vector>
#ifdef HAVE_NEEDLE
extern "C" {
#include "needle.h"
void needle_reset(void) __attribute__((weak));
}
// Lets us pass any pointer type the header declares (char*, unsigned char*...).
struct Any { void* p; template <class T> operator T*() const { return (T*)p; } };
#endif

extern "C" {

JNIEXPORT jint JNICALL Java_com_sterling_app_NeedleEngine_nLoad(JNIEnv* env, jobject, jobject buf, jlong len) {
#ifdef HAVE_NEEDLE
    void* p = env->GetDirectBufferAddress(buf);
    if (!p) return -100;
    return (jint) needle_load(Any{p}, (unsigned long long) len);
#else
    return -1;
#endif
}

JNIEXPORT jint JNICALL Java_com_sterling_app_NeedleEngine_nInit(JNIEnv* env, jobject, jstring sys, jstring tools) {
#ifdef HAVE_NEEDLE
    const char* s = env->GetStringUTFChars(sys, nullptr);
    const char* t = env->GetStringUTFChars(tools, nullptr);
    int r = needle_init(Any{(void*) s}, Any{(void*) t}, Any{nullptr});
    env->ReleaseStringUTFChars(sys, s);
    env->ReleaseStringUTFChars(tools, t);
    return r;
#else
    return -1;
#endif
}

JNIEXPORT jstring JNICALL Java_com_sterling_app_NeedleEngine_nComplete(JNIEnv* env, jobject, jstring text, jint maxNew) {
#ifdef HAVE_NEEDLE
    const char* in = env->GetStringUTFChars(text, nullptr);
    std::vector<char> out(32768, 0);
    int r = needle_complete(Any{(void*) in}, Any{nullptr}, 0, (int) maxNew, Any{out.data()}, (int) out.size() - 1);
    env->ReleaseStringUTFChars(text, in);
    if (r < 0) return nullptr;
    return env->NewStringUTF(out.data());
#else
    return nullptr;
#endif
}

JNIEXPORT jstring JNICALL Java_com_sterling_app_NeedleEngine_nError(JNIEnv* env, jobject) {
#ifdef HAVE_NEEDLE
    const char* e = needle_last_error();
    return env->NewStringUTF(e ? e : "");
#else
    return env->NewStringUTF("needle not built in");
#endif
}

JNIEXPORT void JNICALL Java_com_sterling_app_NeedleEngine_nReset(JNIEnv*, jobject) {
#ifdef HAVE_NEEDLE
    if (needle_reset) needle_reset();
#endif
}

}

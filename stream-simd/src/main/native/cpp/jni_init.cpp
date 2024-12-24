#include "jni_logger.h"
#include <jni.h>

#define JNI_VERSION JNI_VERSION_1_8

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION) != JNI_OK) {
        return JNI_ERR;
    }
    
    jni_cout << "JNI library loaded successfully!" << std::endl;

    return JNI_VERSION;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM* vm, void* reserved) {
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION) != JNI_OK) {
        return;
    }

    jni_cout << "JNI library unloaded successfully!" << std::endl;
}


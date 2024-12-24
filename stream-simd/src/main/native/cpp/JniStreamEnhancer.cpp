#include "jni_init.h"
#include "jni_logger.h"
#include <jni.h>
#include <iostream>
#include <cpuid.h>
#include <limits>

extern jint JNI_OnLoad(JavaVM* vm, void* reserved);
extern void JNI_OnUnload(JavaVM* vm, void* reserved);

jlong avxImplementation(jlong* array, jsize length);
jlong sse41Implementation(jlong* array, jsize length);
jlong scalarImplementation(jlong* array, jsize length);

jlong (*getOptimizedImplementation())(jlong*, jsize);

extern "C" JNIEXPORT jlong JNICALL Java_io_github_workload_simd_JniStreamEnhancer_findMaxId(JNIEnv* env, jclass clazz, jlongArray ids) {
    jni_cout << "Entering findMaxId function" << std::endl;

    jsize length = env->GetArrayLength(ids);
    jlong* array = env->GetLongArrayElements(ids, nullptr);

    if (array == nullptr) {
        jni_cout << "Memory allocation failed" << std::endl;
        return std::numeric_limits<jlong>::min();
    }

    jni_cout << "Calling getOptimizedImplementation" << std::endl;
    auto impl = getOptimizedImplementation();
    jni_cout << "Got optimized implementation" << std::endl;

    jlong maxId = impl(array, length);

    jni_cout << "Max ID found: " << maxId << std::endl;

    env->ReleaseLongArrayElements(ids, array, JNI_ABORT);
    jni_cout << "Exiting findMaxId function" << std::endl;
    return maxId;
}

// 检测 AVX2 支持
bool isAvx2Supported() {
    unsigned int eax, ebx, ecx, edx;
    if (__get_cpuid(7, &eax, &ebx, &ecx, &edx)) {
        return (ebx & bit_AVX2) != 0;
    }
    return false;
}

// 检测 SSE4.1 支持
bool isSse41Supported() {
    unsigned int eax, ebx, ecx, edx;
    if (__get_cpuid(1, &eax, &ebx, &ecx, &edx)) {
        return (ecx & bit_SSE4_1) != 0;
    }
    return false;
}

// 获取优化的实现
jlong (*getOptimizedImplementation())(jlong*, jsize) {
    jni_cout << "Selecting optimized implementation..." << std::endl;

    #ifdef __AVX2__
    if (isAvx2Supported()) {
        jni_cout << "AVX2 is supported and enabled" << std::endl;
        return avxImplementation;
    } else {
        jni_cout << "AVX2 is not supported or not enabled" << std::endl;
    }
    #else
    jni_cout << "AVX2 is not enabled in compilation" << std::endl;
    #endif

    #ifdef __SSE4_1__
    if (isSse41Supported()) {
        jni_cout << "SSE4.1 is supported and enabled" << std::endl;
        return sse41Implementation;
    } else {
        jni_cout << "SSE4.1 is not supported or not enabled" << std::endl;
    }
    #else
    jni_cout << "SSE4.1 is not enabled in compilation" << std::endl;
    #endif

    jni_cout << "Falling back to scalar implementation" << std::endl;
    return scalarImplementation;
}

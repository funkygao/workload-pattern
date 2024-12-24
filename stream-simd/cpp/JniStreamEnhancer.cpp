#include <jni.h>
#include <iostream>
#include <cpuid.h>
#include <limits>

jlong avxImplementation(jlong* array, jsize length);
jlong sse41Implementation(jlong* array, jsize length);
jlong scalarImplementation(jlong* array, jsize length);

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
    #ifdef __AVX2__
    if (isAvx2Supported()) {
        std::cout << "Using AVX2 implementation" << std::endl;
        return avxImplementation;
    }
    #endif
    #ifdef __SSE4_1__
    if (isSse41Supported()) {
        std::cout << "Using SSE4.1 implementation" << std::endl;
        return sse41Implementation;
    }
    #endif
    std::cout << "Using scalar implementation" << std::endl;
    return scalarImplementation;
}

extern "C" JNIEXPORT jlong JNICALL Java_com_example_JniStreamEnhancer_findMaxId(JNIEnv* env, jclass clazz, jlongArray ids) {
    jsize length = env->GetArrayLength(ids);
    jlong* array = env->GetLongArrayElements(ids, nullptr);

    if (array == nullptr) {
        // 处理内存分配失败
        return std::numeric_limits<jlong>::min();
    }

    // 调用优化的实现
    jlong maxId = getOptimizedImplementation()(array, length);

    env->ReleaseLongArrayElements(ids, array, JNI_ABORT);
    return maxId;
}


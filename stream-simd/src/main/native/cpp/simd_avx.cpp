#include <jni.h>
#include <algorithm>
#include <limits>

#ifdef __AVX2__
#include <immintrin.h>

jlong avxImplementation(jlong* array, jsize length) {
    __m256i max_vec = _mm256_set1_epi64x(std::numeric_limits<jlong>::min());
    for (jsize i = 0; i < length; i += 4) {
        __m256i vec = _mm256_loadu_si256(reinterpret_cast<__m256i*>(&array[i]));
        max_vec = _mm256_max_epi64(max_vec, vec);
    }
    alignas(32) jlong max_values[4];
    _mm256_store_si256(reinterpret_cast<__m256i*>(max_values), max_vec);
    return *std::max_element(max_values, max_values + 4);
}
#else
jlong avxImplementation(jlong* array, jsize length) {
    return std::numeric_limits<jlong>::min(); // This will never be called if AVX2 is not supported
}
#endif


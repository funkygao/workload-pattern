#include <jni.h>
#include <algorithm>
#include <limits>

#ifdef __SSE4_1__
#include <smmintrin.h>

jlong sse41Implementation(jlong* array, jsize length) {
    jlong max_value = std::numeric_limits<jlong>::min();
    for (jsize i = 0; i < length; i += 2) {
        __m128i vec = _mm_loadu_si128(reinterpret_cast<__m128i*>(&array[i]));
        
        // 提取两个 64 位整数
        jlong val1 = _mm_extract_epi64(vec, 0);
        jlong val2 = _mm_extract_epi64(vec, 1);
        
        // 使用标量比较
        max_value = std::max({max_value, val1, val2});
    }
    
    // 处理剩余的单个元素（如果长度为奇数）
    if (length % 2 != 0) {
        max_value = std::max(max_value, array[length - 1]);
    }
    
    return max_value;
}
#else
jlong sse41Implementation(jlong* array, jsize length) {
    return std::numeric_limits<jlong>::min(); // This will never be called if SSE4.1 is not supported
}
#endif


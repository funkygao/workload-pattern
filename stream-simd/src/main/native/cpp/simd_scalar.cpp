#include <jni.h>
#include <algorithm>
#include <limits>

jlong scalarImplementation(jlong* array, jsize length) {
    jlong maxId = std::numeric_limits<jlong>::min();
    for (jsize i = 0; i < length; i++) {
        if (array[i] > maxId) {
            maxId = array[i];
        }
    }
    return maxId;
}


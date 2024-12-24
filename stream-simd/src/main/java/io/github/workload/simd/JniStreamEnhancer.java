package io.github.workload.simd;

class JniStreamEnhancer {
    private static final String JNI_SO = "jni_stream_enhancer";

    static {
        // -Djava.library.path=
        System.loadLibrary(JNI_SO);
    }

    public static native long findMaxId(long[] ids);
}

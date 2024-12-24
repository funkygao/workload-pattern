package io.github.workload.simd;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class JniStreamEnhancer {
    private static final String JNI_STREAM_ENHANCER = "jni_stream_enhancer";
    private static final String PATH_ENV = "java.library.path";

    private static boolean isLoaded = false;

    static {
        try {
            log.info("Attempting to load JNI library: {} in {}", JNI_STREAM_ENHANCER, System.getProperty(PATH_ENV));
            System.loadLibrary(JNI_STREAM_ENHANCER); // -Djava.library.path
            isLoaded = true;
            log.info("JNI library loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            log.error("Failed to load JNI library: {}", e.getMessage());
        }
    }

    static boolean isAvailable() {
        return isLoaded;
    }

    public static native long findMaxId(long[] ids);
    public static native long findMinId(long[] ids);
    public static native long countLong(long[] ids);
}

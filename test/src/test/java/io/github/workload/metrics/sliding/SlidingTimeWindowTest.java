package io.github.workload.metrics.sliding;

import io.github.workload.BaseTest;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

class SlidingTimeWindowTest extends BaseTest {

    @Test
    void demo() {
        setLogLevel(Level.TRACE);
        final int windowDurationMs = 1000;
        SimpleErrorSlidingTimeWindow slidingWindow = new SimpleErrorSlidingTimeWindow(5, windowDurationMs);
        log.info("{}", slidingWindow);
        long t = 0;
        for (int i = 0; i < 200; i++) {
            long timeMillis = i * 100;
            slidingWindow.currentBucket(timeMillis);
            slidingWindow.currentBucket(timeMillis + ThreadLocalRandom.current().nextInt(300));
            t = timeMillis;
        }

        // simulate clock drift backwards
        slidingWindow.currentBucket(t - windowDurationMs * 5);
    }

    @Test
    void calculateBucketIdx() {
        final int windowDurationMs = 1000;
        SimpleErrorSlidingTimeWindow window = new SimpleErrorSlidingTimeWindow(5, windowDurationMs);
        assertEquals(0, window.calculateBucketIdx(0));
        assertEquals(1, window.calculateBucketIdx(200));
        assertEquals(1, window.calculateBucketIdx(201));
        assertEquals(4, window.calculateBucketIdx(900));
        assertEquals(0, window.calculateBucketIdx(1000));
        assertEquals(0, window.calculateBucketIdx(1001));
    }

    @Test
    void has_only_one_bucket() {
        final int intervalMs = 1000;
        SlidingTimeWindow<Object> window = new SlidingTimeWindow<Object>(1, intervalMs) {
            @Override
            protected Object newEmptyBucketData(long timeMillis) {
                return "";
            }

            @Override
            protected Bucket<Object> resetBucket(Bucket<Object> bucket, long startTimeMillis) {
                return bucket;
            }
        };

        setLogLevel(Level.TRACE);
        for (int timeMillis = 10; timeMillis < 10_000; timeMillis += intervalMs) {
            assertTrue(window.currentBucket(timeMillis).isTimeInBucket(timeMillis));
        }
    }

    @RepeatedTest(1)
    void basic() {
        setLogLevel(Level.TRACE);

        final int intervalInMs = 1000;
        //    0         1       2        3        4
        // +-------+--------+--------+--------+-------+
        // |  B    |   B    |   B    |   B    |   B   |  例如，B：[200, 400)
        // +-------+--------+--------+--------+-------+
        // 0      200      400      600      800    1000
        SimpleErrorSlidingTimeWindow window = new SimpleErrorSlidingTimeWindow(5, intervalInMs);
        assertNull(window.currentBucket(-1));

        long timeMillis = 0;
        Bucket<SimpleErrorSlidingTimeWindow.SimpleErrorCounter> bucket = window.currentBucket(timeMillis);
        assertEquals(0, bucket.startMillis());
        assertNotNull(bucket.data());
        timeMillis = 199;
        assertSame(bucket, window.currentBucket(timeMillis));
        // 跨intervalInMs周期还是该bucket，而且已经被reset了，因此内存指向同一个地址
        assertSame(bucket, window.currentBucket(timeMillis + intervalInMs));
        assertEquals(intervalInMs, bucket.startMillis());
        timeMillis = 200; // 边界条件：左闭右开
        Bucket<SimpleErrorSlidingTimeWindow.SimpleErrorCounter> bucket1 = window.currentBucket(timeMillis);
        assertNotSame(bucket1, bucket);
        assertEquals(200, bucket1.startMillis());
        timeMillis = intervalInMs; // 又一个窗口(不是bucket)周期
        bucket1 = window.currentBucket(timeMillis);
        assertEquals(1000, bucket1.startMillis());
        assertFalse(bucket1.isTimeInBucket(530));
        assertSame(bucket, bucket1);
        timeMillis = 201;
        assertEquals(200, window.currentBucket(timeMillis).startMillis());
        timeMillis = 500;
        assertEquals(400, window.currentBucket(timeMillis).startMillis());
        timeMillis = 640;
        assertEquals(600, window.currentBucket(timeMillis).startMillis());
        timeMillis = 799;
        assertSame(window.currentBucket(640), window.currentBucket(timeMillis));
        timeMillis = 900;
        bucket = window.currentBucket(timeMillis); // [800, 1000)
        assertEquals(800, bucket.startMillis());
        assertTrue(bucket.isTimeInBucket(800)); //  左闭
        assertFalse(bucket.isTimeInBucket(1000)); // 右开
        assertTrue(bucket.isTimeInBucket(timeMillis));
        assertFalse(bucket.isTimeInBucket(timeMillis + intervalInMs)); // 下一个周期了
        assertTrue(bucket.isTimeInBucket(999));
        assertEquals("WindowBucket(durationMs=200, windowStart=800)", bucket.toString());
    }

}

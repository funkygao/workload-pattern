package com.netflix;

import com.netflix.concurrency.limits.limit.Gradient2Limit;
import com.netflix.concurrency.limits.limit.GradientLimit;
import com.netflix.concurrency.limits.limit.VegasLimit;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * https://github.com/Netflix/concurrency-limits
 *
 * @see <a href="https://netflixtechblog.medium.com/performance-under-load-3e6fa9a60581">Performance Under Load</a>
 */
class ConcurrencyEvaluationTest {

    // https://github.com/apache/brpc/blob/master/docs/cn/auto_concurrency_limiter.md
    void explain_limit_algo() {
        // Gradient限流算法是计算无负载时的RTT与当前RTT的比值来判断是否出现请求排队的情况，类似 CoDel
        // gradient = RTT_no_load / RTT_actual，梯度小于1则说明开始排队了，需要降低limit
        // newLimit = currentLimit * gradient + queueSize, queueSize是当前limit的sqrt
        GradientLimit.newDefault().onSample(0, 23, 100, false);

        // GradientLimit在heavy request场景下不好，容易出现过度保护问题
        // Gradient2Limit缓解了该问题：LongRTT
        Gradient2Limit.newDefault();

        // VegasLimit直接使用等待队列的大小queueSize进行判断是否限流
        // queueSize = limit * (1 - RTT_no_load / RTT_actual)
        // VegasLimit也存在过度保护问题
        VegasLimit.newDefault();
    }

    @Test
    void Gradient2Limit_demo() {
        Gradient2Limit limit = Gradient2Limit.newDefault();
        limit = Gradient2Limit.newBuilder()
                .maxConcurrency(200)
                .build();
        limit.onSample(0, TimeUnit.SECONDS.toNanos(10), 5000, false);
        assertEquals(20, limit.getLimit());
        limit.onSample(0, TimeUnit.SECONDS.toNanos(10), 6000, false);
        assertEquals(21, limit.getLimit());
    }

    @Test
    void VegasLimit_demo() {
        VegasLimit limit = VegasLimit.newBuilder()
                .initialLimit(10000)
                .maxConcurrency(20000)
                .build();
        limit.onSample(0, TimeUnit.SECONDS.toNanos(10), 5000, false);
        assertEquals(10000, limit.getLimit());
        limit.onSample(0, TimeUnit.SECONDS.toNanos(10), 6000, false);
        assertEquals(10024, limit.getLimit());
        limit.onSample(0, TimeUnit.SECONDS.toNanos(20), 3000, true);
        assertEquals(10020, limit.getLimit());
    }

    // (maxPass * windows * minRttMs) / 1000
    //
    // 100ms为1个窗口，maxPass 是过去50个窗口(5s)里，admitted 最多的那个窗口里的 admitted value
    // minRttMs是以窗口为单位的最小平均请求耗时
    // windows 是1秒有多少个窗口，这里为10
    // 例如：maxPass=500, minRttMs=50，则当前系统容量(并发数)：250
    //
    // maxPass * window：每秒成功处理的请求数(在所有窗口内)
    // minRttMs / 1000：把 minRttMs 时间单位转换为秒，以便能与请求数进行计算
    //                  把这个理解为时间跨度：一个请求的时间跨度为 minRttMs
    // 公式整体内涵：在最小rtt时间内，系统能够并发处理的请求数量
    // maxPass * window，是QPS，是把该window平铺到1秒上，那么在任何一个 minRttMs 的时间区间内，请求的数量可以表示为：
    // QPS * minRttMs / 1000
    //
    // 总结：该公式计算的是在最小往返时间内，系统能够并发处理的请求数量。通过将每秒的请求数均匀分布在时间线上，并考虑请求的最小往返时间，可以估算出系统的并发处理能力。
    private void calculateMaxConcurrency() {

    }

}

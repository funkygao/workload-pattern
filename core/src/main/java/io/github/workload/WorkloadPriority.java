package io.github.workload;

import io.github.workload.annotations.Immutable;
import io.github.workload.annotations.VisibleForTesting;
import lombok.AllArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 工作负荷优先级，first citizen of overload protection mechanism.
 * <p>
 * <p>It can be applied on RPC Request, MQ Message, AsyncTask, anything you name it that is runnable.</p>
 * <p>First-class notion of our RPC system and propagated automatically.</p>
 * <pre>
 * ⠀⠀⠀⠀⠀⠀⠀⡄⠀⠀⠀⠀⠀⠀⠀⠀⠀⢠⣿⣿⣶⣶⣄⠀⠀⠀⠀⠀⠀⠀
 * ⠀⠀⠀⠀⠀⠀⠀⣿⡄⠀⠀⠀⠀⠀⠀⠀⠀⢸⣿⣿⣿⣿⣿⣷⡄⣶⣇⠀⠀⠀
 * ⠀⠀⠀⠀⠀⠀⠀⠿⠧⠀⠀⠀⠀⠀⢠⣷⡄⢹⣿⣿⣿⣿⣿⣿⣷⣿⣿⠀⠀⠀
 * ⠀⠀⠀⠀⢶⣶⣶⣶⣶⡖⠀⠀⠀⠀⣼⣿⣿⣸⣿⣿⣿⣿⣿⣿⣿⣿⣿⠀⠀⠀user perceived/production block
 * ⠀⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤
 * ⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⣰⣶⣶⣶⣶⣶⣶⣶⣶⡄⢰⣶⣶⣶⡆⠀⠀defer execution acceptable
 * ⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⣿⣿⣿⣿⡌⣿⣿⣿⣿⡇⣿⣿⣿⣿⠁⠀⠀leverage effect
 * ⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⣸⣿⣿⣿⣇⣿⣿⣿⣿⢸⣿⣿⣿⡿⠀⠀⠀
 * ⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⣰⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⡇⠀⠀⠀
 * ⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⢸⣿⣿⣿⠘⣿⣿⣿⣿⣿⣿⣿⣿⢹⣿⠃⠀⠀⠀
 * ⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠸⣿⣿⡟⠀⣿⣿⣿⣿⣿⣿⣿⣿⠘⠁⠀⠀⠀⠀
 * ⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠙⠉⠀⠀⢿⣿⣿⣿⣿⢿⣿⣿⠀⠀⠀⠀⠀⠀
 * ⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠈⠻⣿⣿⡏⠘⠿⠛⠀⠀⠀⠀⠀⠀
 * ⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠉⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
 * </pre>
 */
@Immutable
@ToString
@Slf4j
public class WorkloadPriority {
    private static final long HALF_HOUR_MS = TimeUnit.MILLISECONDS.convert(30, TimeUnit.MINUTES);

    private static final int PRIORITY_BITS = 7;
    private static final int MAX_7BIT_VALUE = (1 << PRIORITY_BITS) - 1;

    public static final int MAX_P = ofLowest().P(); // 16383

    private static Map<Integer, UState> uStates = new ConcurrentHashMap<>();

    private final int B;
    private final int U;

    private WorkloadPriority(int b, int u) {
        B = b;
        U = u;
    }

    @VisibleForTesting
    static WorkloadPriority of(int b, int u) throws IllegalArgumentException {
        if (b > MAX_7BIT_VALUE || u > MAX_7BIT_VALUE || b < 0 || u < 0) {
            throw new IllegalArgumentException("Out of range for B or U");
        }

        return new WorkloadPriority(b, u);
    }

    /**
     * 基于{@link #U}特征值，定时随机生成优先级，但{@link #B}不变.
     *
     * @param b   {@link #B()}
     * @param uid u值特征，例如 {@code "foo".hashCode()}
     * @return 一个{@link #U}随机的优先级
     */
    public static WorkloadPriority ofPeriodicRandomFromUID(int b, int uid) {
        return ofPeriodicRandomFromUID(b, uid, HALF_HOUR_MS);
    }

    /**
     * 创建一个优先级最低的{@link WorkloadPriority}.
     */
    public static WorkloadPriority ofLowest() {
        return of(MAX_7BIT_VALUE, MAX_7BIT_VALUE);
    }

    /**
     * 根据P值反序列化以重建一个新的{@link WorkloadPriority}.
     *
     * @param P 一个 14 位整数，其中高 7 位用来表示 `B` 值，低 7 位用来表示 `U` 值
     * @return
     * @throws IllegalArgumentException
     */
    public static WorkloadPriority fromP(int P) throws IllegalArgumentException {
        if (P > MAX_P || P < 0) {
            throw new IllegalArgumentException("Invalid P");
        }

        int b = (P >> PRIORITY_BITS) & MAX_7BIT_VALUE; // 获取 B 的高 7 位
        int u = P & MAX_7BIT_VALUE; // 获取 U 的低 7 位
        return of(b, u);
    }

    /**
     * 第一层准入机制，Business Use Case Layer Priority.
     * <p>
     * <p>7-bit integer, lower number signifies a higher priority: [0, 127]</p>
     * <p>它反映的服务的重要性和用户体验.</p>
     */
    public int B() {
        return B;
    }

    /**
     * 第二层准入机制, User Layer Priority.
     * <p>
     * <p>7-bit integer, lower number signifies a higher priority: [0, 127]</p>
     * <p>如果只有{@code B}，粒度太粗，会造成这样的问题：</p>
     * <p>由于过载，某个{@code B}的请求要全部抛弃，这很快把负载降下来；随后它的请求不再抛弃，马上再次过载，如此反复jitter.</p>
     * <p>{@code U}本质上是更细粒度的过载保护控制：partial discarding request of {@code B}.</p>
     */
    public int U() {
        return U;
    }

    /**
     * The normalized priority value.
     *
     * <p>优先级降维：二维变一维，值越小优先级越高.</p>
     *
     * <p>它代表的是：workload delay execution tolerance</p>
     * <p>a 14-bit integer，[0, 16383]</p>
     * <p>可以使用该值进行序列化传递，并通过{@link #fromP(int)}反序列化</p>
     */
    @ToString.Include
    public int P() {
        // +--------+--------+
        // |  B(8)  |  U(8)  |
        // +--------+--------+
        // 合并 B 和 U 为 14 位整数
        return (B << PRIORITY_BITS) | U;
    }

    @VisibleForTesting
    static WorkloadPriority ofPeriodicRandomFromUID(int b, int uid, long timeWindowMs) {
        int normalizedStableU = (uid & Integer.MAX_VALUE) % MAX_7BIT_VALUE;
        long nowMs = SystemClock.ofPrecisionMs(timeWindowMs).currentTimeMillis();
        UState us = uStates.compute(normalizedStableU, (key, presentValue) -> {
            if (presentValue == null || nowMs - presentValue.createdAtMs > timeWindowMs) {
                int randomU = ThreadLocalRandom.current().nextInt(MAX_7BIT_VALUE);
                log.trace("b:{}, create random U:{} for uid:{}", b, randomU, uid);
                return new UState(randomU, nowMs);
            } else {
                // log.debug("b:{} reuse U:{} for uid:{}", b, presentValue.U, uid);
                return presentValue;
            }
        });

        // GC the stale states: steal instruction cycle
        int gcCandidateKey = ThreadLocalRandom.current().nextInt(MAX_7BIT_VALUE);
        uStates.computeIfPresent(gcCandidateKey, (key, presentValue) -> {
            if (nowMs - presentValue.createdAtMs > timeWindowMs) {
                if (log.isDebugEnabled()) {
                    log.debug("Scavenge stale key:{}, {}", gcCandidateKey, presentValue);
                }
                return null;
            } else {
                return presentValue;
            }
        });

        return of((b & Integer.MAX_VALUE) % MAX_7BIT_VALUE, us.U);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }

        if (!(o instanceof WorkloadPriority)) {
            return false;
        }

        WorkloadPriority that = (WorkloadPriority) o;
        return P() == that.P();
    }

    @AllArgsConstructor
    @ToString
    private static class UState {
        final int U;
        final long createdAtMs;
    }

}

package io.github.workload;

import io.github.workload.annotations.Immutable;
import io.github.workload.annotations.VisibleForTesting;
import lombok.AllArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 工作负荷优先级，first citizen of overload fair protection mechanism.
 *
 * <p>具体应用时，优先级更多地反映了workload的时效性要求：timeliness.</p>
 * <p>It is immutable and global visible：这要求它在进程内、进程间进行隐式透明传递.</p>
 * <p>下层/游以上层/游为准，因为上层具有更准确的场景和体验感知能力.</p>
 * <p>It can be applied on RPC Request, MQ Message, AsyncTask, anything you name it that is runnable.</p>
 * <p>
 * <p>该机制赋能混部，提升部署密度，增加goodput.</p>
 * <ol>Every workload/request has two costs:
 * <li>The cost to perform the work (the direct cost)</li>
 * <li>The cost to not perform the work (the opportunity cost)</li>
 * </ol>
 * <p>{@link WorkloadPriority} denominates the opportunity cost.</p>
 *
 * @see <a href="https://sre.google/sre-book/handling-overload/">Google SRE Handling Overload</a>
 */
@Immutable
@ToString
@Slf4j
public class WorkloadPriority {
    private static final String CLOCK_WHO = WorkloadPriority.class.getSimpleName();
    private static final long HALF_HOUR_MS = TimeUnit.MILLISECONDS.convert(30, TimeUnit.MINUTES);

    private static final int PRIORITY_BITS = 7;
    private static final int MAX_7BIT_VALUE = (1 << PRIORITY_BITS) - 1;
    private static final WorkloadPriority LOWEST = new WorkloadPriority(MAX_7BIT_VALUE, MAX_7BIT_VALUE);

    public static final int MAX_P = ofLowest().P(); // 16383

    /**
     * directly user-facing，如果该类请求失败，会造成用户明显感知的用户体验下降，降低SLO.
     */
    public static final int B_CRITICAL_PLUS = 5;

    /**
     * directly user-facing，默认的同步请求优先级.
     * <p>
     * <p>该类请求失败，可能会造成用户感知的影响，但没有{@link #B_CRITICAL_PLUS}那么明显和严重.</p>
     * <p>通常调用者有自动重试机制并可以信赖，并可以容忍几分钟的execution delay：best effort.</p>
     * <p>容量规划和capacity provision，应以{@link #B_CRITICAL}以上(含)优先级请求为标准.</p>
     */
    public static final int B_CRITICAL = 10;

    /**
     * non-interactive retryable requests.
     * <p>
     * <p>可以被defer execution的请求优先级，延迟容忍度在10分钟以上，小时以内.</p>
     * <p>Batch operations(例如，后台图片缩放)的默认优先级.</p>
     * <p>This signals that a request is not directly user-facing and that the user generally doesn't mind if the handling is delayed several minutes, or even an hour.</p>
     * <p>As long as the throttling period is not prolonged and the retries are completing within your processing SLO there’s no real reason to spend more money to serve them more promptly.</p>
     * <p>That said, if your service is throttling traffic for 12 hours every day, it may be time to do something about its capacity.</p>
     */
    public static final int B_SHEDDABLE_PLUS = 20;

    /**
     * 可能经常被drop的请求优先级，但最终会通过重试完成执行.
     * <p>
     * <p>延迟容忍度在1小时以上.</p>
     */
    public static final int B_SHEDDABLE = 40;

    private static final Map<Integer, WorkloadPriority> warmPool = new HashMap<>(4 * MAX_7BIT_VALUE);
    static {
        for (int b : new int[]{B_CRITICAL_PLUS, B_CRITICAL, B_SHEDDABLE_PLUS, B_SHEDDABLE}) {
            for (int u = 0; u < MAX_7BIT_VALUE; u++) {
                warmPool.put(bu2p(b, u), new WorkloadPriority(b, u));
            }
        }
    }

    // at most 128 keys, periodic consistent cohorting
    private static final Map<Integer /* U */, UState> uStates = new ConcurrentHashMap<>();

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

        WorkloadPriority priority = warmPool.get(bu2p(b, u));
        if (priority != null) {
            return priority;
        }

        return new WorkloadPriority(b, u);
    }

    /**
     * 基于{@link #U}特征值，定时随机生成优先级，但{@link #B}不变.
     *
     * @param b   {@link #B()}
     * @param uid u值特征，例如 {@code "foo".hashCode()}, can be negative
     * @return 一个{@link #U}随机的优先级
     */
    public static WorkloadPriority ofPeriodicRandomFromUID(int b, int uid) {
        return ofPeriodicRandomFromUID(b, uid, HALF_HOUR_MS);
    }

    /**
     * 创建一个优先级最低的{@link WorkloadPriority}.
     */
    public static WorkloadPriority ofLowest() {
        return LOWEST;
    }

    public boolean isLowest() {
        return LOWEST == this || P() == MAX_P;
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
     * <p>可以类比IP Header ToS(type of service) field.</p>
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
        return bu2p(B, U);
    }

    private static int bu2p(int b, int u) {
        // +--------+--------+
        // |  B(7)  |  U(7)  |
        // +--------+--------+
        return (b << PRIORITY_BITS) | u;
    }

    public String simpleString() {
        return "priority(P=" + P() + ",B=" + B + ")";
    }

    @VisibleForTesting
    static WorkloadPriority ofPeriodicRandomFromUID(int b, int uid, long timeWindowMs) {
        int normalizedStableU = (uid & Integer.MAX_VALUE) % MAX_7BIT_VALUE;
        long nowMs = SystemClock.ofPrecisionMs(timeWindowMs, CLOCK_WHO).currentTimeMillis();
        UState us = uStates.compute(normalizedStableU, (key, presentValue) -> {
            if (presentValue == null || nowMs - presentValue.createdAtMs > timeWindowMs) {
                int randomU = ThreadLocalRandom.current().nextInt(MAX_7BIT_VALUE);
                log.trace("b:{}, create random U:{} for uid:{}", b, randomU, uid);
                return new UState(randomU, nowMs);
            } else {
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

    /**
     * 基于目标P值生成目标优先级.
     *
     * @param targetP 目标P值
     */
    public WorkloadPriority deriveFromP(int targetP) {
        if (this.P() == targetP) {
            return this;
        }

        return fromP(targetP);
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

    @Override
    public int hashCode() {
        return P();
    }

    @AllArgsConstructor
    @ToString
    private static class UState {
        final int U;
        final long createdAtMs;
    }

    @VisibleForTesting
    static void resetForTesting() {
        uStates.clear();
    }

}

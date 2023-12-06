package io.github.workload.overloading;

import io.github.workload.SystemClock;
import io.github.workload.overloading.annotations.Immutable;
import io.github.workload.overloading.annotations.ThreadSafe;
import lombok.AllArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 工作负荷优先级，面向QoS公平过载保护的一等公民.
 * <p>
 * <p>It can be applied on RPC Request, MQ Message, AsyncTask, anything you name it that is runnable.</p>
 * <pre>
 * ⠀⠀⠀⠀⠀⠀⠀⡄⠀⠀⠀⠀⠀⠀⠀⠀⠀⢠⣿⣿⣶⣶⣄⠀⠀⠀⠀⠀⠀⠀
 * ⠀⠀⠀⠀⠀⠀⠀⣿⡄⠀⠀⠀⠀⠀⠀⠀⠀⢸⣿⣿⣿⣿⣿⣷⡄⣶⣇⠀⠀⠀
 * ⠀⠀⠀⠀⠀⠀⠀⠿⠧⠀⠀⠀⠀⠀⢠⣷⡄⢹⣿⣿⣿⣿⣿⣿⣷⣿⣿⠀⠀⠀
 * ⠀⠀⠀⠀⢶⣶⣶⣶⣶⡖⠀⠀⠀⠀⣼⣿⣿⣸⣿⣿⣿⣿⣿⣿⣿⣿⣿⠀⠀⠀user perceived/production block
 * ⠀⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤⠤
 * ⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⣰⣶⣶⣶⣶⣶⣶⣶⣶⡄⢰⣶⣶⣶⡆⠀⠀defer execution acceptable
 * ⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⣿⣿⣿⣿⡌⣿⣿⣿⣿⡇⣿⣿⣿⣿⠁⠀⠀
 * ⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⣸⣿⣿⣿⣇⣿⣿⣿⣿⢸⣿⣿⣿⡿⠀⠀⠀
 * ⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⣰⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⡇⠀⠀⠀
 * ⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⢸⣿⣿⣿⠘⣿⣿⣿⣿⣿⣿⣿⣿⢹⣿⠃⠀⠀⠀
 * ⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠸⣿⣿⡟⠀⣿⣿⣿⣿⣿⣿⣿⣿⠘⠁⠀⠀⠀⠀
 * ⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠙⠉⠀⠀⢿⣿⣿⣿⣿⢿⣿⣿⠀⠀⠀⠀⠀⠀
 * ⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠈⠻⣿⣿⡏⠘⠿⠛⠀⠀⠀⠀⠀⠀
 * ⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠉⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
 * </pre>
 */
@ThreadSafe
@Immutable
@Slf4j
@ToString
public class WorkloadPriority {
    private static final long HALF_HOUR_MS = TimeUnit.MILLISECONDS.convert(30, TimeUnit.MINUTES);

    private static final int MAX_VALUE = (1 << 7) - 1; // 127
    // (B, U)的最低优先级值：值越大优先级越低
    private static final int LOWEST_PRIORITY = MAX_VALUE;
    private static final int MAX_P = ofLowestPriority().P(); // 32639
    private static final int U_BITS = 8;

    // {u: lastMs}
    private static Map<Integer, UState> uStates = new ConcurrentHashMap<>();
    private static Random random = new Random();

    /**
     * 第一层准入机制，Business Use Case Layer Priority.
     * <p>
     * <p>取值范围：[0, 127]</p>
     * <p>它反映的服务的重要性和用户体验.</p>
     */
    private final int B;

    /**
     * 第二层准入机制, User Layer Priority.
     * <p>
     * <p>如果只有{@code B}，粒度太粗，会造成这样的问题：</p>
     * <p>由于过载，某个{@code B}的请求要全部抛弃，这很快把负载降下来；随后它的请求不再抛弃，马上再次过载，如此反复jitter.</p>
     * <p>{@code U}本质上是更细粒度的过载保护控制：partial discarding request of {@code B}.</p>
     */
    private final int U;

    private WorkloadPriority(int b, int u) {
        B = b;
        U = u;
    }

    /**
     * 创建指定二级优先级值的工作负荷优先级.
     *
     * @param b user-specified 7-bit integer, lower number signifies a higher priority
     * @param u user-specified 7-bit integer, lower number signifies a higher priority
     * @return a new priority instance
     * @throws IllegalArgumentException
     */
    static WorkloadPriority of(int b, int u) throws IllegalArgumentException {
        if (b > MAX_VALUE || u > MAX_VALUE) {
            throw new IllegalArgumentException("Out of range for B or U");
        }

        return new WorkloadPriority(b, u);
    }

    /**
     * 基于{@link #U}每半小时随机生成优先级，但{@link #B}不变.
     *
     * @param b           {@link #B()}
     * @param uIdentifier u值特征，例如 {@code "foo".hashCode()}
     * @return 一个u值随机的优先级
     */
    public static WorkloadPriority ofStableRandomU(int b, int uIdentifier) {
        return timeRandomU(b, uIdentifier, HALF_HOUR_MS);
    }

    static WorkloadPriority ofLowestPriority() {
        return of(LOWEST_PRIORITY, LOWEST_PRIORITY);
    }

    public static WorkloadPriority fromP(int P) throws IllegalArgumentException {
        if (P > MAX_P || P < 0) {
            throw new IllegalArgumentException("Invalid P");
        }

        int b = P >> U_BITS;
        int u = P & 0xFF;
        return of(b, u);
    }

    public int B() {
        return B;
    }

    public int U() {
        return U;
    }

    /**
     * 优先级降维：二维变一维，值越小优先级越高.
     *
     * <p>它代表的是：workload delay execution tolerance</p>
     * <p>a 14-bit integer</p>
     * <p>可以使用该值进行序列化传递，并通过{@link #fromP(int)}反序列化</p>
     */
    public int P() {
        // +--------+--------+
        // |  B(8)  |  U(8)  |
        // +--------+--------+
        return (B << U_BITS) | U;
    }

    static WorkloadPriority timeRandomU(int b, int uIdentifier, long timeWindowMs) {
        int u = (uIdentifier & Integer.MAX_VALUE) % MAX_VALUE;
        long nowMs = SystemClock.ofPrecisionMs(timeWindowMs).currentTimeMillis();
        UState us = uStates.get(u);
        if (us == null) {
            us = new UState(u, nowMs); // 首次则不做随机 TODO 如何使用者不是hashCode怎么办?
            uStates.putIfAbsent(u, us);
        } else if (nowMs - us.createdAtMs > timeWindowMs) {
            // time to roll time window, randomize U
            us = new UState(random.nextInt(MAX_VALUE), nowMs);
            uStates.put(u, us);
        } else {
            // keep the u unchanged
            // GC the stale states: steal instruction cycle
            int gcCandidateKey = random.nextInt(MAX_VALUE);
            if (gcCandidateKey != us.U) {
                UState gcCandidate = uStates.get(gcCandidateKey);
                if (gcCandidate != null && nowMs - gcCandidate.createdAtMs > timeWindowMs) {
                    // stale unused: GC, scavenge
                    log.debug("scavenge key:{} U state:{}", gcCandidateKey, gcCandidate);
                    uStates.remove(gcCandidateKey);
                }
            }
        }

        return of((b & Integer.MAX_VALUE) % MAX_VALUE, us.U);
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

package io.github.workload.overloading;

import lombok.AllArgsConstructor;

import java.io.Serializable;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 二层的工作负荷优先级 for QoS.
 * <p>
 * <p>It can be RPC Request, MQ Message, Task, anything you name it that is runnable.</p>
 * <p>prioritise a given set of traffic from all different clients without starving critical traffic.</p>
 * <p>由{@code entry service}决定，并通过隐式传参传递给下游子请求，子请求继承父请求的优先级.</p>
 * <p>{@link WorkloadPriority}代表的用户视角的请求优先级，而不是被分解的子请求优先级，子请求优先级取决于entry service的.</p>
 * <p>例如：用户下单，该请求会产生扣减库存的子请求，库存系统处理时拿到的{@link WorkloadPriority}是{@code 用户下单}.</p>
 * <p>minimize the waste of computational resources spent on the partial processing of service tasks</p>
 * <ul>How to prioritize workload?
 * <li>prefer scheduling requests for which a user is waiting over non-interactive requests</li>
 * </ul>
 */
public class WorkloadPriority implements Serializable {
    private static final long serialVersionUID = 6373611532663483048L;

    private static final long MsInHour = TimeUnit.MILLISECONDS.convert(60, TimeUnit.MINUTES);

    private static final int MAX_VALUE = (1 << 7) - 1; // 127
    private static final int MAX_P = (MAX_VALUE << 8) | MAX_VALUE; // 32639

    // {u: lastMs}
    private static Map<Integer, UState> uMap = new ConcurrentHashMap<>();
    private static SystemClock clock = new SystemClock(1000, "WorkloadPriority Clock");
    private static Random random = new Random();

    /**
     * (B, U)的最低优先级值：值越大优先级越低.
     */
    private static final int LOWEST_PRIORITY = MAX_VALUE;

    /**
     * 第一层准入机制.
     * <p>
     * <p>[0, 127]</p>
     * <p>bucket layer admission control</p>
     * <p>它反映的服务的重要性和用户体验.</p>
     * <ul>例如，在微信里：
     * <li>Login服务有最高优先级，因为所有请求都需要登录后才能执行</li>
     * <li>Pay服务优先级比收发消息更高，因为用户对钱相关操作更敏感，容忍度更低</li>
     * </ul>
     * <p>它代表的是入口请求的优先级，人为事先配置；如果不.配置，代表它采用最低优先级</p>
     */
    private final int B;

    /**
     * 第二层准入机制.
     * <p>
     * <p>例如，同一个报表查询接口，它的{@code B}是固定的，但可以根据是否查询热数据动态调整{@code U}值：越新鲜的数据优先级越高</p>
     * <p>如果只有{@code B}，粒度太粗，会造成这样的问题：</p>
     * <p>由于过载，某个{@code B}的请求要全部抛弃，这很快把负载降下来；随后它的请求不再抛弃，马上再次过载，如此反复.</p>
     * <p>因此，增加{@code U}，本质上是更细粒度的过载保护控制：partial discarding request of B.</p>
     */
    private final int U;

    private WorkloadPriority(int b, int u) {
        B = b;
        U = u;
    }

    /**
     * 创建指定二级优先级值的工作负荷优先级.
     *
     * @param b
     * @param u
     * @return
     * @throws IllegalArgumentException
     */
    public static WorkloadPriority of(int b, int u) throws IllegalArgumentException {
        if (b > MAX_VALUE || u > MAX_VALUE) {
            throw new IllegalArgumentException("Out of range for B or U");
        }

        return new WorkloadPriority(b, u);
    }

    /**
     * 基于{@link #U}每小时变化地随机生成优先级，但{@link #B}不变.
     */
    public static WorkloadPriority ofHourlyRandomU(int b, int u) {
        u = (u & Integer.MAX_VALUE) % MAX_VALUE;
        long nowMs = clock.currentTimeMillis();
        UState us = uMap.get(u);
        if (us == null) {
            us = new UState(u, nowMs); // 首次就使用用户传入的u，而不做随机
            uMap.putIfAbsent(u, us);
        } else if (nowMs - us.createdAtMs > MsInHour) {
            // 1h期限已到，更改其值
            us = new UState(random.nextInt(MAX_VALUE), nowMs);
        } else {
            // keep the u unchanged
        }

        return of((b & Integer.MAX_VALUE) % MAX_VALUE, us.actual);
    }

    /**
     * 创建一个拥有准入管制豁免权的工作负荷优先级.
     * <p>
     * <p>即，具有最高优先级：在过载时拦住其他请求，让有豁免权的请求通行.</p>
     */
    public static WorkloadPriority ofExempt() {
        return of(0, 0);
    }

    /**
     * 创建一个最低优先级，过载时首先拒绝这类工作负荷.
     */
    public static WorkloadPriority ofLowestPriority() {
        return of(LOWEST_PRIORITY, LOWEST_PRIORITY);
    }

    static WorkloadPriority fromP(int P) throws IllegalArgumentException {
        if (P > MAX_P || P < 0) {
            throw new IllegalArgumentException("Invalid P");
        }

        int b = P >> 8;
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
     */
    public int P() {
        // +--------+--------+
        // |  B(8)  |  U(8)  |
        // +--------+--------+
        return (B << 8) | U;
    }

    @AllArgsConstructor
    private static class UState {
        final int actual;
        final long createdAtMs;
    }

}

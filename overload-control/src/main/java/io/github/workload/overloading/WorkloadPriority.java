package io.github.workload.overloading;

import java.io.Serializable;

/**
 * 二层请求优先级 for QoS.
 *
 * <p>It can be RPC Request, MQ Message, Task, anything you name it.</p>
 * <p>prioritise a given set of traffic from all different clients without starving critical traffic.</p>
 * <p>由{@code entry service}决定，并通过隐式传参传递给下游子请求，子请求继承父请求的优先级.</p>
 * <p>{@link WorkloadPriority}代表的用户视角的请求优先级，而不是被分解的子请求优先级，子请求优先级取决于entry service的.</p>
 * <p>例如：用户下单，该请求会产生扣减库存的子请求，库存系统处理时拿到的{@link WorkloadPriority}是{@code 用户下单}.</p>
 * <p>minimize the waste of computational resources spent on the partial processing of service tasks</p>
 */
public class WorkloadPriority implements Serializable {
    private static final long serialVersionUID = 6373611532663483048L;

    private static final int MAX_VALUE = (1 << 7) - 1;
    private static final int MAX_P = (MAX_VALUE << 8) | MAX_VALUE; // 32639

    /**
     * (B, U)的最低优先级值：值越大优先级越低.
     */
    public static final int LOWEST_PRIORITY = MAX_VALUE;

    /**
     * Predefined business layer priority：第一层准入机制.
     *
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
     * 创建一个拥有准入管制豁免权的请求优先级.
     */
    public static WorkloadPriority ofExempt() {
        return new WorkloadPriority(0, 0);
    }

    public static WorkloadPriority ofLowestPriority() {
        return new WorkloadPriority(LOWEST_PRIORITY, LOWEST_PRIORITY);
    }

    /**
     * User layer priority：第二层准入机制.
     *
     * <p>[0, 127]</p>
     * <p>不同于{@link #B}，{@link #U}是动态计算的</p>
     * <p>例如，同一个报表查询接口，它的B是固定的，但可以根据是否查询热数据动态调整U值：越新鲜的数据优先级越高</p>
     * <p>如果只有{@code B}，粒度太粗，会造成这样的问题：</p>
     * <p>由于过载，某个{@code B}的请求要全部抛弃，这很快把负载降下来；随后它的请求不再抛弃，马上再次过载，如此反复.</p>
     * <p>因此，增加{@code U}，本质上是更细粒度的过载保护控制：partial discarding request of B.</p>
     * <pre>
     * │<────────────────────── high priority ──────────────────────────────
     * │<───── B=0 ─────>│<──────────────── B=3 ────────────────>│<─  B=8 ─>
     * +─────────────────+───────────────────────────────────────+──────────
     * │ 0 │ 5 │ 8 │ 127 │ 1 │ 2 │ 7 │ 12 │ 50 │ 101 │ 102 │ 115 │ ......
     * +─────────────────+───────────────────────────────────────+──────────
     *   │   │                              │
     *   U   U                              │
     *                              AdmissionLevel cursor
     * AdmissionLevel游标=(3, 50)，意味着，所有B>3的请求被抛弃，所有(B=3, U>50)的请求被抛弃
     * 移动该游标，向左意味着负载加剧，向右意味着负载减轻
     * </pre>
     */
    private final int U;

    public static WorkloadPriority of(int b, int u) throws IllegalArgumentException {
        if (b > MAX_VALUE || u > MAX_VALUE) {
            throw new IllegalArgumentException("Out of range for B or U");
        }

        return new WorkloadPriority(b, u);
    }

    static WorkloadPriority fromP(int P) throws IllegalArgumentException {
        if (P > MAX_P || P < 0) {
            throw new IllegalArgumentException("Invalid P");
        }

        int b = P >> 8;
        int u = P & 0xFF;
        return of(b, u);
    }

    private WorkloadPriority(int b, int u) {
        B = b;
        U = u;
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
     * <p>例如，P0优先级比P1优先级高</p>
     */
    public int P() {
        // +--------+--------+
        // |  B(8)  |  U(8)  |
        // +--------+--------+
        return (B << 8) | U;
    }
}

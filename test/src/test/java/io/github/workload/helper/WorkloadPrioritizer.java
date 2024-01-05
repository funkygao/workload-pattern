package io.github.workload.helper;

import io.github.workload.overloading.WorkloadPriority;

import java.util.concurrent.ThreadLocalRandom;

public class WorkloadPrioritizer {

    /**
     * 如果该类请求失败，会造成用户明显感知的用户体验下降，降低SLO.
     *
     * <p>它会引起用户/WMS上游系统的被动重试，而这种重试是不可靠的，不能假设的：它们可能不会重试.</p>
     * <p>而不重试，可能造成业务的中断、停滞等后果.</p>
     */
    private static int CRITICAL_PLUS = 5;

    /**
     * 默认的同步请求优先级.
     *
     * <p>该类请求失败，可能会造成用户感知的影响，但没有{@link WorkloadPrioritizer#CRITICAL_PLUS}那么明显和严重.</p>
     * <p>通常调用者有自动重试机制并可以信赖，并可以容忍几分钟的execution delay：best effort.</p>
     * <p>容量规划和capacity provision，应以{@link WorkloadPrioritizer#CRITICAL}以上(含)优先级请求为标准.</p>
     */
    private static int CRITICAL = 10;

    /**
     * 可以被defer execution的请求优先级，延迟容忍度在10分钟以上，小时以内.
     */
    private static int SHEDDABLE_PLUS = 20;

    /**
     * 可能经常被drop的请求优先级，但最终会通过重试完成执行.
     *
     * <p>延迟容忍度在1小时以上.</p>
     */
    private static int SHEDDABLE = 40;

    private static int randomUid() {
        return ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE);
    }

    public static WorkloadPriority randomMQ() {
        int b = SHEDDABLE_PLUS;
        return WorkloadPriority.ofPeriodicRandomFromUID(b, randomUid());
    }

    public static WorkloadPriority randomRpc() {
        int b = CRITICAL;
        return WorkloadPriority.ofPeriodicRandomFromUID(b, randomUid());
    }

    public static WorkloadPriority randomWeb() {
        int b = CRITICAL_PLUS;
        return WorkloadPriority.ofPeriodicRandomFromUID(b, randomUid());
    }

    public static WorkloadPriority randomLowPriority() {
        return WorkloadPriority.ofPeriodicRandomFromUID(SHEDDABLE, randomUid());
    }

}

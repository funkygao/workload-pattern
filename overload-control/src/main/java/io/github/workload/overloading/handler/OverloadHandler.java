package io.github.workload.overloading.handler;

import io.github.workload.annotations.ThreadSafe;

/**
 * 过载处理器：Client在遇到Server过载响应后该如何处理.
 *
 * <p>Usage example:</p>
 * <pre>
 * {@code
 * OverloadHandler handler = OverloadHandler.ofRetryBudget(5 * 60);
 *
 * ResponseMessage responseMessage = getNext().invoke(requestMessage);
 * String service = requestMessage.getInvocationBody().getClazzName();
 * handler.recordRequest(service);
 * if (responseMessage.isOverloaded() && handler.canRetry(service, 0.1)) {
 *     // retry the request: SLB might dispatch it to another idle server node
 * }
 * }
 * </pre>
 */
@ThreadSafe
public interface OverloadHandler {

    /**
     * 记录某个服务发送了一次请求.
     *
     * @param service 服务名称
     */
    void recordRequest(String service);

    /**
     * 在指定预算下是否可以发起重试，以便可能分发到低负载节点.
     *
     * <ul>重试预算设置取决于多个因素：
     * <li>服务的重要性：对于关键服务，可能希望设置较高的重试预算，以确保在可能的情况下尽量完成请求</li>
     * <li>失败的成本：如果一个请求失败的成本很高，例如，它可能导致数据丢失或用户体验显著下降，那么可能需要一个较高的重试预算</li>
     * <li>重试带来的额外负载：重试会增加系统的总体负载，特别是在高并发的情况下。需要评估系统是否能够处理这种额外的负载</li>
     * <li>依赖服务的限制：如果你的服务依赖于其他服务，那么你的重试策略可能需要考虑这些依赖服务的限制和它们的重试策略</li>
     * </ul>
     *
     * @param service 服务名称
     * @param budget  该服务的重试预算，(0.0, 1.0)，0.1表示10%的重试预算，每百次请求只能重试10次
     * @return true if you can retry
     */
    boolean canRetry(String service, double budget);

    /**
     * 创建一个新的基于重试预算的过载处理器.
     */
    static OverloadHandler ofRetryBudget() {
        return new BudgetedRetryHandler(5 * 60);
    }
}

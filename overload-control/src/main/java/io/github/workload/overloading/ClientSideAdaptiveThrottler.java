package io.github.workload.overloading;

/**
 * Client side adaptive throttling.
 *
 * <p>当某个客户端检测到最近的请求错误中的一大部分都是由于“配额不足”错误导致时，该客户端开始自行限制请求速度，限制它自己生成请求的数量</p>
 * <p>超过这个请求数量限制的请求直接在本地回复失败，而不会真正发到网络层</p>
 */
class ClientSideAdaptiveThrottler {
    private static final double defaultMultiplier = 2.0;

    double rejectProbability(int requests, int accepts, double multiplier) {
        return Math.max(0, (requests - multiplier * accepts) / (requests + 1));
    }

    /**
     * Client request rejection probability.
     *
     * @param requests The number of requests attempted by the application layer
     * @param accepts  The number of requests accepted by the backend
     * @return
     * @see <a href="https://sre.google/sre-book/handling-overload/#eq2101">Google Client request rejection probability</a>
     */
    double rejectProbability(int requests, int accepts) {
        return this.rejectProbability(requests, accepts, defaultMultiplier);
    }

}

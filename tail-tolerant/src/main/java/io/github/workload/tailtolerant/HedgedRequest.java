package io.github.workload.tailtolerant;

/**
 * 对冲请求.
 *
 * <p>In short: the client first sends one request, but then sends an additional request after a timeout if the previous hasn't returned an answer in the expected time.</p>
 * <p>The client cancels remaining requests once the first result is received.</p>
 * <p>A simple way to curb latency variability: within readonly request short-term adaptation</p>
 */
public class HedgedRequest {
    /**
     *
     * <p>until the first request has been outstanding for more than the TP95 expected latency for this class of requests</p>
     * <p>这只会额外增加整体的5%负荷，却有效提升了长尾响应性</p>
     */
    private int defer;
}

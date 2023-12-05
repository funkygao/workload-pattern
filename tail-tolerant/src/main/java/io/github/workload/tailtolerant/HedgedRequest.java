package io.github.workload.tailtolerant;

/**
 * 对冲请求，用来消除长尾延迟.
 *
 * <p>In short: the client first sends one request, but then sends an additional request after a timeout if the previous hasn't returned an answer in the expected time.</p>
 */
class HedgedRequest {

    /**
     * 正常请求在多少时间内未响应，才发起对冲请求.
     *
     * <p>如果以TP95为准，这只会额外增加后端5%负荷，却有效提升了长尾响应性</p>
     * <p>如果以TP50为准，会增加后端50%的压力</p>
     */
    private int deferMs;
}

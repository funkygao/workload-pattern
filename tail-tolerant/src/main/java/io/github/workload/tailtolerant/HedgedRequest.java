package io.github.workload.tailtolerant;

import io.github.workload.NamedThreadFactory;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;

/**
 * 对冲请求，用来消除长尾延迟：TP99毛刺.
 *
 * <p>冲请求是一种在原始请求延迟时通过发送额外的请求来尝试加快获取结果的方法，主要用于读请求.</p>
 * <ul>Side effects:
 * <li>增加资源消耗：发送额外的请求会增加服务器的负载，对于已经负载较高的系统，可能会导致性能进一步下降</li>
 * <li>可能的数据不一致：如果对冲请求和原始请求返回的结果存在差异，需要有额外的逻辑来处理这种不一致性</li>
 * </ul>
 *
 * @param <R> 请求返回的结果类型
 */
@Slf4j
public class HedgedRequest<R> {

    /**
     * 用于在{@link #deferMs}后触发执行对冲请求.
     *
     * <p>它只是用来触发对冲请求，不执行实际的请求任务，发生频率不高，单线程的就够了</p>
     */
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    /**
     * 用于立即执行请求任务.
     */
    private static final ExecutorService executor = new ThreadPoolExecutor(4, 4,
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(2),
            new NamedThreadFactory(HedgedRequest.class.getSimpleName(), true));

    /**
     * 正常请求在多少时间内未响应才发起对冲请求.
     *
     * <p>如果以TP95为准，这只会额外增加后端5%负荷，却有效提升了长尾响应性</p>
     * <p>如果以TP50为准，会增加后端50%的压力</p>
     */
    private final long deferMs;

    public HedgedRequest(long deferMs) {
        this.deferMs = deferMs;
    }

    /**
     * 发送请求并在需要时执行对冲逻辑.
     *
     * @param requestTask 代表要执行的请求
     * @return 返回两个请求中首先完成的结果
     */
    public CompletableFuture<R> call(Callable<R> requestTask) {
        CompletableFuture<R> originalRequest = new CompletableFuture<>();
        CompletableFuture<R> hedgedRequest = new CompletableFuture<>();

        // 提交原始请求
        executor.submit(() -> {
            try {
                R result = requestTask.call();
                originalRequest.complete(result);

                // 取消对冲请求，如果尚未开始
                hedgedRequest.cancel(false);
            } catch (Throwable e) {
                originalRequest.completeExceptionally(e);
            }
        });

        // 延迟提交对冲请求
        scheduler.schedule(() -> {
            if (!originalRequest.isDone()) {
                // 原始请求尚未完成
                log.warn("Original slow, hedged after {}ms", deferMs);
                executor.submit(() -> {
                    try {
                        R result = requestTask.call();
                        hedgedRequest.complete(result);
                    } catch (Throwable e) {
                        hedgedRequest.completeExceptionally(e);
                    }
                });
            }
        }, deferMs, TimeUnit.MILLISECONDS);

        return originalRequest.applyToEither(hedgedRequest, result -> result);
    }

    public static void shutdown() {
        executor.shutdown();
        scheduler.shutdown();
    }

}

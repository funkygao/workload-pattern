package io.github.workload.greedy;

/**
 * 被{@link GreedyLimiter}限流后抛出的异常.
 */
public class GreedyException extends RuntimeException {
    GreedyException(String message) {
        super(message);
    }
}

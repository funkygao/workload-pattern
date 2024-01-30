package io.github.workload;

interface RetryAware {

    /**
     * 这是第几次重试.
     */
    int retryAttempted();
}

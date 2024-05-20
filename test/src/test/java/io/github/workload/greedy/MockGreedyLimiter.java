package io.github.workload.greedy;

class MockGreedyLimiter implements GreedyLimiter {

    @Override
    public boolean canAcquire(String key, int permits) {
        if (key.equals("cannotAcquire")) {
            return false;
        }
        return true;
    }
}

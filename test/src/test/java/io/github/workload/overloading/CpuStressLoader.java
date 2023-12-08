package io.github.workload.overloading;

class CpuStressLoader {

    static void burnCPUs() {
        int numThreads = Runtime.getRuntime().availableProcessors();
        for (int i = 0; i < numThreads; i++) {
            new BusyThread().start(); // 启动多个线程执行繁忙的任务
        }
    }

    private static class BusyThread extends Thread {
        @Override
        public void run() {
            while(true) {
                // the loop makes CPU busy
            }
        }
    }
}

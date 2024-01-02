package io.github.workload.overloading;

import java.util.ArrayList;
import java.util.List;

class CpuStressLoader {
    private static final List<BusyThread> threads = new ArrayList<>(Runtime.getRuntime().availableProcessors());

    static void burnCPUs() {
        int numThreads = Runtime.getRuntime().availableProcessors();
        for (int i = 0; i < numThreads; i++) {
            BusyThread thread = new BusyThread();
            threads.add(thread);
            thread.start();
        }
    }

    static void stop() {
        for (BusyThread thread : threads) {
            thread.interrupt();
        }
        threads.clear();
    }

    private static class BusyThread extends Thread {
        @Override
        public void run() {
            while(!interrupted()) {
                // the loop makes CPU busy
            }
        }
    }
}

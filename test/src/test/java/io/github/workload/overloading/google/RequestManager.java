package io.github.workload.overloading.google;

import java.util.ArrayList;
import java.util.List;

public class RequestManager {
    private static final int HARD_QUOTA = 45;
    private static final int SOFT_QUOTA = 25;
    private static final int STEPS = 10;

    private final double divisor = (double) (HARD_QUOTA - SOFT_QUOTA) / STEPS;

    private int received = 0;
    private int req_modulus = 0;
    private int accepted = 0;
    private int rejected = 0;
    // 假设active_requests是存储请求的列表
    private List<Object> active_requests = new ArrayList<>();

    // 此方法需要具体实现来返回负载。
    public int getLoad() {
        // 这需要你提供具体实现
        return 0; //示例默认返回值，你需要根据实际情况实现
    }

    public void addRequest(Object r) {
        received++;
        req_modulus = (req_modulus + 1) % STEPS;

        // Are we overloaded?
        int load = getLoad();

        // Become progressively more likely to reject requests
        // once load > soft quota; reject everything once load
        // hits hard limit.
        int threshold = (int) ((HARD_QUOTA - load) / divisor);
        if (req_modulus < threshold) {
            // We're not too loaded
            active_requests.add(r);
            accepted++;
        } else {
            rejected++;
        }
    }
}

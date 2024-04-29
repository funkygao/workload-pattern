package io.github.workload.overloading;

import lombok.experimental.UtilityClass;

@UtilityClass
class JVM {

    /**
     * CPU过载静默期：JVM启动期间通常CPU比较高，静默期跳过该时间段.
     *
     * <p>默认值：600，表示10分钟.</p>
     */
    static final String CPU_OVERLOAD_COOL_OFF_SEC = "workload.CPU_OVERLOAD_COOL_OFF_SEC";

    /**
     * CPU使用率过载阈值，即CPU使用率到达多少被认为CPU过载.
     *
     * <p>默认值：0.75，表示75%</p>
     */
    static final String CPU_USAGE_UPPER_BOUND = "workload.CPU_USAGE_UPPER_BOUND";

    /**
     * CPU使用率的EMA平滑系数，用于控制对最近数据变化的敏感度，避免毛刺产生过载保护抖动.
     *
     * <p>默认值：0.25</p>
     */
    static final String CPU_EMA_ALPHA = "workload.CPU_EMA_ALPHA";

    /**
     * 基于排队时间判断过载，超过该平均时长的排队表示过载.
     *
     * <p>默认值：200</p>
     */
    static final String AVG_QUEUED_MS_UPPER_BOUND = "workload.AVG_QUEUED_MS_UPPER_BOUND";

    /**
     * 过载保护的降速因子.
     *
     * <p>默认值：0.05，表示过载时抛弃优先级序列尾部的5%</p>
     */
    static final String SHED_DROP_RATE = "workload.SHED_DROP_RATE";

    /**
     * 过载保护的提速恢复因子.
     *
     * <p>(加速下降/reject request，慢速恢复/admit request)</p>
     * <p>相当于冷却周期，如果没有它会造成负载短时间下降造成大量请求被放行，严重时打满CPU</p>
     *
     * <p>默认值：0.015，即1.5%</p>
     */
    static final String SHED_RECOVER_RATE = "workload.SHED_RECOVER_RATE";

    /**
     * 降速时允许的过度丢弃最大误差率.
     *
     * <p>过度丢弃是因为优先级的数量分布是不均匀的，存在跳动.</p>
     *
     * <p>默认值：1.01，即101%</p>
     *
     * @see <a href="https://www.envoyproxy.io/docs/envoy/latest/intro/arch_overview/upstream/load_balancing/panic_threshold.html">envoy panic threshold</a>
     */
    static final String OVER_SHED_BOUND = "workload.OVER_SHED_BOUND";

    /**
     *
     * <p>默认值：0.5，即50%</p>
     */
    static final String OVER_ADMIT_BOUND = "workload.OVER_ADMIT_BOUND";

    static double getDouble(String key, double defaultValue) {
        String value = System.getProperty(key);
        if (value == null) {
            return defaultValue;
        }

        return Double.parseDouble(value);
    }

    static long getLong(String key, long defaultValue) {
        String value = System.getProperty(key);
        if (value == null) {
            return defaultValue;
        }

        return Long.parseLong(value);
    }
}

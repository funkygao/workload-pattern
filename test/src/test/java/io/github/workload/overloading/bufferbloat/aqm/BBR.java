package io.github.workload.overloading.bufferbloat.aqm;

/**
 * Google's Bottleneck Bandwidth and Round-trip propagation time congestion control algorithm.
 *
 * <p>在BBR之前，都是基于丢包进行流控的，问题：Bufferbloat, overact for transient traffic burst</p>
 * <p>BBR seeks high throughput with a small queue by probing BW and RTT, Not loss-based, delay-based, ECN-based, AIMD-based</p>
 *
 * @see <a href="https://cloud.google.com/blog/products/networking/tcp-bbr-congestion-control-comes-to-gcp-your-internet-just-got-faster">Google BBR</a>
 * @see <a href="https://github.com/google/bbr">BBR github</a>
 */
class BBR {

}

/**
 * Active Queue Management algorithms from networking for battling bufferbloat.
 *
 * <p>Services also have queues (of requests, not packets) and suffer from queueing delay when overloaded.</p>
 * <p>Here we adapt AQM algorithm for services.</p>
 * <p>Performance: (throughput, latency)</p>
 * <p/>
 * <p>AQM does this by proactively dropping packets before buffers become full. </p>
 * <p>This allows routers to absorb traffic bursts while keeping queue sizes small. </p>
 * <p>It also decreases latency, eases congestion, and improves the experience for subscribers.</p>
 */
package io.github.workload.aqm;
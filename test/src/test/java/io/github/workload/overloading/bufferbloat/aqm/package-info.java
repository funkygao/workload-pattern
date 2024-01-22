/**
 * Active Queue Management to solve Buffer bloat issue.
 *
 * <p>AQM algorithms seek to try and drop or reject messages so that you avoid messages sitting in buffers for long periods of time.</p>
 * <p>"Bufferbloat" can be thought of as the buffering of too many packets in flight between two network end points, resulting in excessive delays and confusion of TCP's flow control algorithms.</p>
 * <p>并不等待队列满之后才被动丢弃请求，而是在某个条件触发的情况下主动对请求进行丢弃，以防止类似Bufferbloat现象的发生</p>
 * <p>packet egress path: Classification, Scheduling, Shaping.</p>
 * <p>Scheduling(RED/CoDel): 1) decide which packet to send next, 2) decide what to do when the queues are full</p>
 * <p>Shaping(DRR/HFSC/HTB/CAKE): typically variation of token bucket algorithm</p>
 */
package io.github.workload.overloading.bufferbloat.aqm;
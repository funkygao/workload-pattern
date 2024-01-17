/**
 * Active Queue Management to solve Buffer bloat issue.
 *
 * <p>并不等待队列满之后才被动丢弃请求，而是在某个条件触发的情况下主动对请求进行丢弃，以防止类似Bufferbloat现象的发生</p>
 */
package io.github.workload.overloading.bufferbloat.aqm;
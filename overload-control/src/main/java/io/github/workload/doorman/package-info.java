/**
 * 在服务端拒绝成本不可忽略时进行的客户端主动限流.
 *
 * <p>When a client detects that a significant portion of its recent requests have been rejected due to "out of quota" errors, it starts self-regulating and caps the amount of outgoing traffic it generates.</p>
 * <p>Requests above the cap fail locally without even reaching the network.</p>
 *
 * @see <a href="https://github.com/youtube/doorman">Doorman, a solution for Global Distributed Client Side Rate Limiting</a>
 */
package io.github.workload.doorman;
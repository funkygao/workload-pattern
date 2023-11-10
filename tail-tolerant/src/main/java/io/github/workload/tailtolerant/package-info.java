/**
 * Latency tail-tolerant.
 *
 * <ul>Variability of response time that leads to high tail latency can arise for many reasons:
 * <li>Shared resources</li>
 * <li>Background daemons</li>
 * <li>Maintenance activities</li>
 * <li>Queuing</li>
 * <li>Garbage collection</li>
 * </ul>
 * <ul>提升响应性的确定性：
 * <li>为服务分优先级：用户交互的请求往往对响应性有更稳定期望</li>
 * <li>降低线头阻塞：high-level服务处理请求往往有widely varying intrinsic costs，把大请求拆分成相互交叉的小请求</li>
 * <li>管理好后台任务，避免与服务正面竞争</li>
 * </ul>
 *
 * @see <a href="https://www.barroso.org/publications/TheTailAtScale.pdf">The Tail at Scale</a>
 */
package io.github.workload.tailtolerant;
/**
 * 基于QoS公平的自适应过载保护算法，应对brief burst/skewed traffic.
 *
 * <p>Load shedding is a technique that allows your system to serve nominal capacity, regardless of how much traffic is being sent to it, in order to maintain availability.</p>
 *
 * <ol>Load shedding principles:
 * <li>Sustain peak performance</li>
 * <li>Isolation: misbehaving customers never hurt anyone(but themselves)</li>
 * <li>Prioritize requests clearly</li>
 * <li>Customer quotas only enforced when needed</li>
 * <li>Cost based: don't model capacity with QPS</li>
 * <li>Retries: per-request max attempts(e,g. 3), per-backend retry budget(e,g. 10% of all requests)</li>
 * </ol>
 *
 * @see <a href="https://hormozk.com/capacity">Capacity control toolbox</a>
 * @see <a href="https://aws.amazon.com/cn/builders-library/using-load-shedding-to-avoid-overload/">Using load shedding to avoid overload</a>
 * @see <a href="https://www.cs.columbia.edu/~ruigu/papers/socc18-final100.pdf">微信的过载保护</a>
 * @see <a href="https://www.usenix.org/legacy/publications/library/proceedings/usits03/tech/full_papers/welsh/welsh_html/usits.html">Adaptive Overload Control for Busy Internet Servers</a>
 * @see <a href="http://www.abelay.me/data/breakwater_osdi20.pdf">Overload Control for μs-Scale RPCs with Breakwater</a>
 * @see <a href="https://sre.google/sre-book/handling-overload/">Google SRE Handling Overload</a>
 * @see <a href="https://cloud.google.com/blog/products/gcp/using-load-shedding-to-survive-a-success-disaster-cre-life-lessons">Google Using load shedding to survive a success disaster</a>
 * @see <a href="http://iheartradio.github.io/kanaloa/docs/theories.html">Kanaloa</a>
 */
package io.github.workload.overloading;

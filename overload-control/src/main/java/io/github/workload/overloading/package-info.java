/**
 * 基于QoS公平的自适应过载保护算法，应对brief burst/skewed traffic.
 *
 * <p>实现基于业务公平的高效柔性服务.</p>
 * <ul>Where does the burst traffic come from?
 * <li>Over time</li>
 * <li>Over space</li>
 * </ul>
 *
 * @see <a href="https://aws.amazon.com/cn/builders-library/using-load-shedding-to-avoid-overload/">Using load shedding to avoid overload</a>
 * @see <a href="https://www.cs.columbia.edu/~ruigu/papers/socc18-final100.pdf">微信的过载保护</a>
 * @see <a href="https://www.usenix.org/legacy/publications/library/proceedings/usits03/tech/full_papers/welsh/welsh_html/usits.html">Adaptive Overload Control for Busy Internet Servers</a>
 * @see <a href="http://www.abelay.me/data/breakwater_osdi20.pdf">Overload Control for μs-Scale RPCs with Breakwater</a>
 * @see <a href="https://sre.google/sre-book/handling-overload/">Google SRE Handling Overload</a>
 * @see <a href="https://cloud.google.com/blog/products/gcp/using-load-shedding-to-survive-a-success-disaster-cre-life-lessons">Google Using load shedding to survive a success disaster</a>
 * @see <a href="http://iheartradio.github.io/kanaloa/docs/theories.html">Kanaloa</a>
 */
package io.github.workload.overloading;

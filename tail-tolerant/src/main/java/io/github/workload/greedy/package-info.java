/**
 * Greedy workload regulation.
 *
 * <p>贪婪工作负荷(贪婪请求/大报文)管控组件.</p>
 * <ul>它们来自哪里？对于入参，通过JSR303就可以解决了，但更复杂更隐蔽的场景有：
 * <li>RPC请求的Response，里面有size很大的集合对象：通过RPC(e,g. dubbo)内置filter机制管控</li>
 * <li>DB查询，ResultSet结果级很大：可以通过Mybatis Interceptor机制管控</li>
 * <li>方法内部的循环遍历：这是greedy组件主战场.</li>
 * </ul>
 * @deprecated Use {@link io.github.workload.safe} instead.
 */
package io.github.workload.greedy;
package io.github.workload;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class DemoCostAwareAspect {

    public Object costBasedAdmissionControl(ProceedingJoinPoint pjp) throws Throwable {
        Object[] args = pjp.getArgs();
        if (args == null || args.length == 0) {
            return pjp.proceed();
        }

        for (Object arg : args) {
            if (arg instanceof CostAware) {
                CostAware costAware = (CostAware) arg;
                if (!admit(costAware)) {
                    throw new RuntimeException("Throttled");
                }
            }
        }

        return pjp.proceed();
    }

    private boolean admit(CostAware costAware) {
        final String ownerNo = costAware.tag();
        final int cost = costAware.cost();
        if ("xxx".equals(ownerNo) && cost > 10) {
            return false;
        }

        return true;
    }
}

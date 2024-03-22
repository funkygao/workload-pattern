package io.github.workload;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.SourceLocation;
import org.aspectj.runtime.internal.AroundClosure;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CostAwareTest {

    @Test
    void basic() throws Throwable {
        CostAwareAspect aspect = new CostAwareAspect();

        // will be throttled because of many items
        final int manyItems = 2;
        final ProceedingJoinPoint joinPointCostly = new ShipmentOrderProceedingJoinPoint(manyItems);
        Exception expected = assertThrows(RuntimeException.class, () -> {
            aspect.costBasedAdmissionControl(joinPointCostly);
        });
        assertEquals("Throttled", expected.getMessage());

        // cheap workload will not be throttled
        final int fewItems = 1;
        final ProceedingJoinPoint cheap = new ShipmentOrderProceedingJoinPoint(fewItems);
        aspect.costBasedAdmissionControl(cheap);
    }

    private static class ShipmentOrder implements CostAware {
        private String orderNo;
        private String merchantNo;
        private String ownerNo;
        private List<String> items;

        @Override
        public int cost() {
            return items.size();
        }

        @Override
        public String tag() {
            return ownerNo;
        }
    }

    @Aspect
    public static class CostAwareAspect {

        @Around("execution(* *.*(..))")
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
            if ("xxx".equals(ownerNo) && cost > 1) {
                return false;
            }

            return true;
        }
    }

    static class ShipmentOrderProceedingJoinPoint implements ProceedingJoinPoint {
        private final int items;

        ShipmentOrderProceedingJoinPoint(int items) {
            this.items = items;
        }


        @Override
        public void set$AroundClosure(AroundClosure aroundClosure) {

        }

        @Override
        public Object proceed() throws Throwable {
            return null;
        }

        @Override
        public Object proceed(Object[] objects) throws Throwable {
            return null;
        }

        @Override
        public String toShortString() {
            return null;
        }

        @Override
        public String toLongString() {
            return null;
        }

        @Override
        public Object getThis() {
            return null;
        }

        @Override
        public Object getTarget() {
            return null;
        }

        @Override
        public Object[] getArgs() {
            ShipmentOrder so = new ShipmentOrder();
            so.ownerNo = "xxx";
            if (items == 1) {
                so.items = Arrays.asList("sku1");
            } else {
                so.items = Arrays.asList("sku1", "sku2");
            }

            return new Object[]{so};
        }

        @Override
        public Signature getSignature() {
            return null;
        }

        @Override
        public SourceLocation getSourceLocation() {
            return null;
        }

        @Override
        public String getKind() {
            return null;
        }

        @Override
        public StaticPart getStaticPart() {
            return null;
        }
    }

}
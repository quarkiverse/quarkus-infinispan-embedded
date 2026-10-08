package org.infinispan.metrics.impl;

import java.util.function.BooleanSupplier;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(className = "org.infinispan.metrics.impl.MetricsComponentFactory", onlyWith = SubstituteMetricsComponentFactory.MicrometerPrometheusAbsent.class)
public final class SubstituteMetricsComponentFactory {

    @Substitute
    public Object construct(String componentName) {
        return null;
    }

    static class MicrometerPrometheusAbsent implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            try {
                Class.forName("io.micrometer.prometheusmetrics.PrometheusMeterRegistry");
                return false;
            } catch (ClassNotFoundException e) {
                return true;
            }
        }
    }
}

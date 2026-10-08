package org.infinispan.commons.jdkspecific;

import java.util.function.BooleanSupplier;

import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(className = "org.infinispan.commons.jdkspecific.OffHeapMemory", onlyWith = SubstituteOffHeapMemory.IsTargetClassAvailable.class)
@Delete
public final class SubstituteOffHeapMemory {

    static class IsTargetClassAvailable implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            try {
                Class.forName("org.infinispan.commons.jdkspecific.OffHeapMemory");
                return true;
            } catch (ClassNotFoundException e) {
                return false;
            }
        }
    }
}

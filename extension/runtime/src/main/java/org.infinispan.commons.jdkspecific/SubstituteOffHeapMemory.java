package org.infinispan.commons.jdkspecific;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(OffHeapMemory.class)
public final class SubstituteOffHeapMemory {

    @Substitute
    public static org.infinispan.commons.spi.OffHeapMemory getInstance() {
        return UnsafeMemoryAddressOffHeapMemory.getInstance();
    }
}

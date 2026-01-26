package org.infinispan.commons.jdkspecific;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(UnsafeMemoryAddressOffHeapMemory.class)
public final class SubstituteUnsafeMemoryAddressOffHeapMemory {

    @Substitute
    public long allocate(long bytes) {
        return 0L;
    }

    @Substitute
    public void free(long address) {

    }

}

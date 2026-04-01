package org.infinispan.commons.jdkspecific;

import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(className = "org.infinispan.commons.jdkspecific.OffHeapMemory")
@Delete
public final class SubstituteOffHeapMemory {
}

package io.quarkiverse.infinispan.embedded.testlib;

import org.infinispan.protostream.SerializationContext;
import org.infinispan.protostream.SerializationContextInitializer;
import org.infinispan.protostream.schema.Schema;

/**
 * Test SerializationContextInitializer from a separate library module.
 *
 * <p>
 * This class is in a separate Maven module to verify that the InfinispanEmbeddedProcessor
 * correctly discovers SerializationContextInitializer implementations from dependency JARs
 * using CombinedIndexBuildItem (not just ApplicationIndexBuildItem).
 * </p>
 *
 * <h2>Purpose:</h2>
 * <p>
 * This proves that:
 * <ul>
 * <li>CombinedIndexBuildItem scans dependency JARs, not just application code</li>
 * <li>The Jandex index is properly generated for this module (via jandex-maven-plugin)</li>
 * <li>The processor can instantiate initializers from dependencies</li>
 * <li>META-INF/services includes initializers from dependencies</li>
 * </ul>
 * </p>
 *
 * <h2>Test Strategy:</h2>
 * <p>
 * The deployment tests will add this module as a dependency and verify that:
 * <ol>
 * <li>This initializer is discovered during build-time processing</li>
 * <li>It's included in the generated META-INF/services file</li>
 * <li>It's registered with Infinispan's GlobalConfiguration</li>
 * <li>ServiceLoader can discover it at runtime</li>
 * </ol>
 * </p>
 */
public class LibrarySerializationContextInitializer implements SerializationContextInitializer, Schema {

    @Override
    public String getName() {
        return "library-test.proto";
    }

    @Override
    public String getContent() {
        return "syntax = \"proto3\";\npackage io.quarkiverse.infinispan.embedded.testlib;\n\n"
                + "message TestMessage {\n"
                + "  string name = 1;\n"
                + "}\n";
    }

    @Override
    public void registerSchema(SerializationContext serCtx) {
        // No-op for test - schema registration would happen here in real implementation
    }

    @Override
    public void registerMarshallers(SerializationContext serCtx) {
        // No-op for test - marshaller registration would happen here in real implementation
    }
}

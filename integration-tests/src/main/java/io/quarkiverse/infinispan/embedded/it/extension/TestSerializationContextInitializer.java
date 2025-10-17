package io.quarkiverse.infinispan.embedded.it.extension;

import java.io.UncheckedIOException;

import org.infinispan.protostream.SerializationContext;
import org.infinispan.protostream.SerializationContextInitializer;

/**
 * Concrete SerializationContextInitializer for testing META-INF/services generation.
 *
 * <p>
 * This class is used to verify that the InfinispanEmbeddedProcessor correctly generates
 * META-INF/services/org.infinispan.protostream.SerializationContextInitializer files
 * that can be discovered via ServiceLoader.
 * </p>
 *
 * <h2>Purpose:</h2>
 * <p>
 * This concrete implementation ensures that:
 * <ul>
 * <li>The processor can successfully instantiate it during the build phase</li>
 * <li>It gets included in the META-INF/services file</li>
 * <li>It can be discovered via ServiceLoader at runtime</li>
 * </ul>
 * </p>
 */
public class TestSerializationContextInitializer implements SerializationContextInitializer {

    @Override
    public String getProtoFileName() {
        return "test.proto";
    }

    @Override
    public String getProtoFile() throws UncheckedIOException {
        return "syntax = \"proto3\";\npackage test;\n";
    }

    @Override
    public void registerSchema(SerializationContext serCtx) {
        // No-op for test
    }

    @Override
    public void registerMarshallers(SerializationContext serCtx) {
        // No-op for test
    }
}

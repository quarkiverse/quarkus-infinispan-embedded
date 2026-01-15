package io.quarkiverse.infinispan.embedded.it.extension;

import org.infinispan.protostream.SerializationContext;
import org.infinispan.protostream.SerializationContextInitializer;

/**
 * Abstract base class for SerializationContextInitializer implementations.
 *
 * <p>
 * This abstract class serves as a test fixture to verify that the InfinispanEmbeddedProcessor
 * correctly filters out abstract classes during the SerializationContextInitializer discovery
 * and instantiation phase.
 * </p>
 *
 * <h2>Purpose:</h2>
 * <p>
 * This class is intentionally abstract and exists in the test classpath to reproduce and
 * verify the fix for the issue where the processor would attempt to instantiate abstract
 * classes, resulting in {@link InstantiationException} during application startup.
 * </p>
 *
 * <h2>Technical Context:</h2>
 * <p>
 * During the Quarkus build phase, the InfinispanEmbeddedProcessor uses Jandex to scan
 * for all classes implementing {@link SerializationContextInitializer}. Prior to the fix,
 * it would attempt to instantiate ALL discovered classes, including abstract ones. The fix
 * added filtering logic to check the class modifiers and skip abstract classes and interfaces.
 * </p>
 *
 * <h2>Expected Behavior:</h2>
 * <ul>
 * <li><strong>Without the fix:</strong> The processor calls {@code Class.newInstance()} on this
 * abstract class, throwing an {@link InstantiationException} and preventing application startup.</li>
 * <li><strong>With the fix:</strong> The processor detects that this class is abstract using
 * Jandex {@code ClassInfo.flags()} and skips instantiation, allowing the application to
 * start successfully.</li>
 * </ul>
 *
 * <h2>Usage in Tests:</h2>
 * <p>
 * This class should not be instantiated directly. Its presence in the classpath is sufficient
 * to test the processor's filtering logic. Concrete implementations should be created for
 * actual serialization context initialization.
 * </p>
 *
 */
public abstract class AbstractSerializationContextInitializer implements SerializationContextInitializer {

    @Override
    public abstract void registerSchema(SerializationContext serCtx);

    @Override
    public abstract void registerMarshallers(SerializationContext serCtx);
}

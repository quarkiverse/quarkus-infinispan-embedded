package io.quarkiverse.infinispan.embedded.it.extension;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Integration test for the InfinispanEmbeddedProcessor fix.
 * See <a href="https://github.com/quarkiverse/quarkus-infinispan-embedded/issues/45">issue 45</a>
 *
 * <p>
 * This test verifies that the InfinispanEmbeddedProcessor correctly handles abstract classes
 * when discovering and instantiating SerializationContextInitializer implementors during the
 * Quarkus build phase.
 * </p>
 *
 * <h2>Problem Context:</h2>
 * <p>
 * Prior to the fix, InfinispanEmbeddedProcessor would attempt to instantiate ALL classes
 * that implement SerializationContextInitializer, including abstract classes. This caused
 * InstantiationException during application startup when abstract classes were present in
 * the classpath.
 * </p>
 *
 * <h2>The Fix:</h2>
 * <p>
 * The processor now filters out abstract classes before attempting
 * instantiation, using Jandex ClassInfo metadata to check modifiers.
 * </p>
 *
 * <p>
 * If the application starts successfully (indicated by the @QuarkusTest
 * annotation not throwing an exception), it proves that abstract classes are being properly
 * filtered out.
 * </p>
 */

@QuarkusTest
public class InfinispanEmbeddedProcessorTest {

    /**
     * Verifies that the InfinispanEmbeddedProcessor does not attempt to instantiate abstract
     * SerializationContextInitializer classes during the build phase.
     *
     * <p>
     * <strong>Test Mechanics:</strong>
     * </p>
     * <ul>
     * <li>The test classpath contains an abstract class that implements SerializationContextInitializer
     * (e.g., AbstractSerializationContextInitializer)</li>
     * <li>Without the fix: The processor would call newInstance() on the abstract class,
     * causing an InstantiationException and preventing the application from starting</li>
     * <li>With the fix: The processor filters out abstract classes, and the application
     * starts successfully</li>
     * </ul>
     *
     * <p>
     * <strong>Success Criteria:</strong>
     * </p>
     * <ul>
     * <li>The @QuarkusTest annotation successfully initializes the Quarkus application</li>
     * <li>No InstantiationException is thrown during the build phase</li>
     * <li>The test method executes, confirming successful startup</li>
     * </ul>
     *
     * <p>
     * <strong>Note:</strong> If this test fails to even start (throws an exception before
     * the test method runs), it indicates that the processor is still attempting to instantiate
     * abstract classes.
     * </p>
     *
     * @implNote The actual assertion is trivial because the real test is whether the
     *           application context starts successfully. The @QuarkusTest annotation handles
     *           the application lifecycle, and any failure to initialize would prevent this
     *           test method from being executed.
     */
    @Test
    public void shouldNotTryToInstantiateAbstractClassTest() {
        // If we reach here, the application initialized successfully.
        // This confirms that InfinispanEmbeddedProcessor correctly filtered out
        // abstract classes and did NOT attempt to call newInstance() on
        // AbstractSerializationContextInitializer (or any other abstract class).

        // The test assertion is intentionally simple - the real verification
        // is that we reached this point without an InstantiationException.
        assertTrue(true,
                "Application startup succeeded, confirming abstract classes were filtered out");
    }
}

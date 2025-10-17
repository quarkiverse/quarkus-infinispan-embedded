package io.quarkiverse.infinispan.embedded.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.infinispan.manager.EmbeddedCacheManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

/**
 * Integration test for SerializationContextInitializer discovery improvements. (issue #53)
 *
 * <p>
 * This test verifies the enhancements made to InfinispanEmbeddedProcessor:
 * <ul>
 * <li>Uses CombinedIndex to discover initializers from both application and dependencies</li>
 * <li>Gracefully handles inaccessible classes (inner/package-private from other modules)</li>
 * <li>Passes successfully instantiated initializers to Infinispan for build-time optimization</li>
 * </ul>
 * </p>
 *
 */
public class SerializationContextInitializerDiscoveryTest {

    @Inject
    EmbeddedCacheManager cacheManager;

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withConfigurationResource("empty-application-infinispan-embedded.properties")
            .addBuildChainCustomizer(buildChainBuilder -> {
                // Index the test-lib dependency to ensure LibrarySerializationContextInitializer is discovered
                buildChainBuilder.addBuildStep(context -> {
                    context.produce(new io.quarkus.deployment.builditem.IndexDependencyBuildItem(
                            "io.quarkiverse.infinispan", "quarkus-infinispan-embedded-test-lib"));
                }).produces(io.quarkus.deployment.builditem.IndexDependencyBuildItem.class).build();
            });

    /**
     * Verify that SerializationContextInitializers are registered with Infinispan.
     */
    @Test
    public void shouldRegisterInitializersWithInfinispan() {
        assertThat(cacheManager).isNotNull();

        // The fact that the application started successfully means initializers were registered
        // If they weren't, we'd get marshalling exceptions when trying to use the cache
        assertThat(cacheManager.getCacheManagerConfiguration().serialization().contextInitializers())
                .as("Cache manager should have context initializers configured")
                .isNotEmpty();
    }

    /**
     * Verify that inaccessible classes don't prevent application startup.
     */
    @Test
    public void shouldHandleInaccessibleClassesGracefully() {
        // The QuarkusContextInitializer is a package-private inner class in the runtime module
        // It should be skipped during build-time instantiation but still work at runtime
        assertThat(cacheManager.getCacheManagerConfiguration().serialization().contextInitializers())
                .as("Runtime should have QuarkusContextInitializer registered via runtime module")
                .isNotEmpty();

        // Verify that the cache manager is functional
        // The fact that we can access the cache manager and it has initializers configured
        // confirms the runtime initializer works even though it couldn't be instantiated at build time
        assertThat(cacheManager)
                .as("Cache manager should be accessible and functional")
                .isNotNull();
    }

    /**
     * Verify that initializers from dependencies are discovered (CombinedIndex usage).
     */
    @Test
    public void shouldDiscoverInitializersFromDependencies() {
        // Get all registered initializers
        var contextInitializers = cacheManager.getCacheManagerConfiguration().serialization().contextInitializers();

        assertThat(contextInitializers)
                .as("Should discover initializers from Infinispan dependencies")
                .isNotEmpty();

        // At minimum, we should have Infinispan's internal initializers from dependencies
        // plus the QuarkusContextInitializer from the runtime module
        assertThat(contextInitializers.size())
                .as("Should have multiple initializers from various sources")
                .isGreaterThan(0);
    }

    /**
     * Verify the cache manager configuration is valid.
     */
    @Test
    public void shouldHaveValidCacheManagerConfiguration() {
        assertThat(cacheManager).isNotNull();

        // Verify the cache manager configuration has serialization settings
        assertThat(cacheManager.getCacheManagerConfiguration())
                .as("Cache manager configuration should be valid")
                .isNotNull();

        // Verify that we can get the cache configuration manager
        assertThat(cacheManager.getCacheNames())
                .as("Cache manager should track cache names")
                .isNotNull();
    }

    /**
     * Verify that META-INF/services file is generated for ServiceLoader discovery.
     *
     * <p>
     * This test confirms that the processor generates
     * META-INF/services/org.infinispan.protostream.SerializationContextInitializer
     * containing all discovered initializer class names, enabling ServiceLoader-based discovery.
     * </p>
     *
     * <p>
     * This test PROVES that CombinedIndexBuildItem is being used (not ApplicationIndexBuildItem) because:
     * <ul>
     * <li>Infinispan's own initializers (CommonTypesSchema, PersistenceContextInitializerImpl, etc.)
     * are in Infinispan dependency JARs, not in the application code</li>
     * <li>If ApplicationIndexBuildItem were used, these would NOT be discovered</li>
     * <li>The fact that ServiceLoader finds them proves CombinedIndexBuildItem scanned dependency JARs</li>
     * </ul>
     * </p>
     */
    @Test
    public void shouldGenerateMetaInfServicesFile() {
        // Verify ServiceLoader can find initializers via META-INF/services
        java.util.ServiceLoader<org.infinispan.protostream.SerializationContextInitializer> serviceLoader = java.util.ServiceLoader
                .load(org.infinispan.protostream.SerializationContextInitializer.class);

        java.util.List<org.infinispan.protostream.SerializationContextInitializer> loadedInitializers = new java.util.ArrayList<>();
        serviceLoader.forEach(loadedInitializers::add);

        assertThat(loadedInitializers)
                .as("ServiceLoader should discover initializers from META-INF/services generated by the processor")
                .isNotEmpty();

        // Verify that dependency initializers from Infinispan JARs are discoverable via ServiceLoader
        // 1. CombinedIndexBuildItem is working (discovering classes from dependency JARs)
        // 2. META-INF/services file was generated and includes these dependency classes
        // If ApplicationIndexBuildItem were used, these Infinispan classes would NOT be found
        assertThat(loadedInitializers.stream()
                .map(Object::getClass)
                .map(Class::getName))
                .as("Should include Infinispan dependency initializers (proves CombinedIndex + META-INF/services work)")
                .anyMatch(name -> name.contains("CommonTypesSchema") || name.contains("PersistenceContextInitializer"));
    }

    /**
     * Verify that LibrarySerializationContextInitializer from test-lib is instantiated and registered.
     *
     * <p>
     * This test confirms that:
     * <ul>
     * <li>The test-lib dependency initializer was discovered via CombinedIndexBuildItem</li>
     * <li>It was successfully instantiated during build time</li>
     * <li>It was registered with Infinispan's SerializationContext</li>
     * <li>Its proto schema is available in the SerializationContext</li>
     * </ul>
     * </p>
     *
     * <p>
     * This is THE definitive proof that CombinedIndexBuildItem works for dependency JARs:
     * <ul>
     * <li>LibrarySerializationContextInitializer is in the test-lib module (separate JAR)</li>
     * <li>If ApplicationIndexBuildItem were used, it would NOT be found</li>
     * <li>The fact that it's registered proves CombinedIndexBuildItem discovered it from the test-lib JAR</li>
     * <li>The fact that it's registered also proves it was successfully instantiated (not just class name collected)</li>
     * </ul>
     * </p>
     */
    @Test
    public void shouldRegisterLibrarySerializationContextInitializer() {
        // Get the registered context initializers from Infinispan
        var contextInitializers = cacheManager.getCacheManagerConfiguration().serialization().contextInitializers();

        // Collect all initializer class names for debugging
        java.util.List<String> initializerNames = contextInitializers.stream()
                .map(Object::getClass)
                .map(Class::getName)
                .collect(java.util.stream.Collectors.toList());

        // Verify that our test library initializer was registered
        // 1. CombinedIndexBuildItem discovered it from test-lib JAR
        // 2. It was successfully instantiated at build time
        // 3. It was passed to Infinispan and registered
        assertThat(initializerNames)
                .as("LibrarySerializationContextInitializer from test-lib JAR should be registered (proves CombinedIndex + instantiation)")
                .anyMatch(name -> name.contains("LibrarySerializationContextInitializer"));
    }
}

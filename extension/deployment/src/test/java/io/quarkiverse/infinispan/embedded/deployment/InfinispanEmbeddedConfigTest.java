package io.quarkiverse.infinispan.embedded.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.inject.Default;

import org.infinispan.manager.EmbeddedCacheManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;

public class InfinispanEmbeddedConfigTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withConfigurationResource("empty-application-infinispan-embedded.properties");

    @Test
    public void embeddedCacheManagerAccessible() {
        EmbeddedCacheManager embeddedCacheManager = Arc.container()
                .instance(EmbeddedCacheManager.class, Default.Literal.INSTANCE).get();
        assertThat(embeddedCacheManager).isNotNull();
        assertThat(embeddedCacheManager.isCoordinator()).isTrue();
        assertThat(embeddedCacheManager.getAccessibleCacheNames()).isEmpty();
    }
}

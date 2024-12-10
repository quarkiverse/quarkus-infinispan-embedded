package io.quarkiverse.infinispan.embedded.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.inject.Default;

import org.infinispan.manager.EmbeddedCacheManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;

public class InfinispanEmbeddedNotClusteredTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withConfigurationResource("not-clustered-application-infinispan-embedded.properties");

    @Test
    public void notClustered() {
        EmbeddedCacheManager cacheManager = Arc.container().instance(EmbeddedCacheManager.class, Default.Literal.INSTANCE)
                .get();
        assertThat(cacheManager).isNotNull();
        assertThat(cacheManager.isCoordinator()).isFalse();
    }
}

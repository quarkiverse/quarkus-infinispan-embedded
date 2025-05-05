package io.quarkiverse.infinispan.embedded.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;

import org.infinispan.AdvancedCache;
import org.infinispan.Cache;
import org.infinispan.manager.EmbeddedCacheManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.infinispan.embedded.Embedded;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;

public class InfinispanEmbeddedConfigTest {

    @Inject
    @Embedded("cache1")
    Cache<String, String> cache1;

    @Inject
    @Embedded("cache2")
    Cache<String, String> cache2;

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withConfigurationResource("empty-application-infinispan-embedded.properties");

    @Test
    public void embeddedCacheManagerAccessible() {
        assertThat(Arc.container().instance(EmbeddedCacheManager.class, Default.Literal.INSTANCE).get()).isNotNull();
        assertThat(Arc.container().listAll(AdvancedCache.class).size()).isEqualTo(2);
    }

    @Test
    public void cachesAreAccessible() {
        assertThat(cache1).isNotNull();
        assertThat(cache2).isNotNull();
    }
}

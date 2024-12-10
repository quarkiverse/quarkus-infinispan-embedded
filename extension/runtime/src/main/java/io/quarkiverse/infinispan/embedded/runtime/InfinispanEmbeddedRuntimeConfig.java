package io.quarkiverse.infinispan.embedded.runtime;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "quarkus.infinispan-embedded")
public interface InfinispanEmbeddedRuntimeConfig {

    /**
     * The configured Infinispan embedded xml file which is used by the managed EmbeddedCacheManager and its Caches
     */
    Optional<String> xmlConfig();

    /**
     * Sets a cluster with defaults.
     */
    @WithDefault("true")
    boolean clustered();
}

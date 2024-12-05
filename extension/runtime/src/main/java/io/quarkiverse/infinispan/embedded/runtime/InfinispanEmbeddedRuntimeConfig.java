package io.quarkiverse.infinispan.embedded.runtime;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "infinispan-embedded")
public interface InfinispanEmbeddedRuntimeConfig {

    /**
     * The configured Infinispan embedded xml file which is used by the managed EmbeddedCacheManager and its Caches
     */
    Optional<String> xmlConfig();

}

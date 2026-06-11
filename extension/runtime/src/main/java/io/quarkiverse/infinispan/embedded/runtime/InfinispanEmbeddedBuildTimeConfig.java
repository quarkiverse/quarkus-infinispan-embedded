package io.quarkiverse.infinispan.embedded.runtime;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
@ConfigMapping(prefix = "quarkus.infinispan-embedded")
public interface InfinispanEmbeddedBuildTimeConfig {

    /**
     * Whether a health check is published in case the smallrye-health extension is present.
     */
    @WithDefault("true")
    boolean healthEnabled();
}

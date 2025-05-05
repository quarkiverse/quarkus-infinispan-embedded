package io.quarkiverse.infinispan.embedded.runtime.cache;

import java.time.Duration;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface InfinispanCacheRuntimeConfig {
    /**
     * The default lifespan of the item stored in the cache.
     * This value determines how long the item will be retained in the cache since
     * its creation, regardless of access.
     * If present, this overrides the default lifespan configured in the cache configuration.
     */
    Optional<Duration> lifespan();

    /**
     * The default max-idle time of the item stored in the cache.
     * This value determines how long the item can remain idle (not accessed) before it
     * is considered expired.
     * If present, this overrides the default max-idle value configured in the cache configuration.
     * If both max-idle and lifespan are present,max-idle must be less than lifespan.
     */
    Optional<Duration> maxIdle();
}

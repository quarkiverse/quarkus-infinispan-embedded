package io.quarkiverse.infinispan.embedded.runtime.health;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;
import org.infinispan.health.CacheHealth;
import org.infinispan.health.ClusterHealth;
import org.infinispan.health.Health;
import org.infinispan.health.HealthStatus;
import org.infinispan.manager.EmbeddedCacheManager;

@Readiness
@ApplicationScoped
public class InfinispanEmbeddedHealthCheck implements HealthCheck {

    @Inject
    EmbeddedCacheManager cacheManager;

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder builder = HealthCheckResponse
                .named("Infinispan Embedded cluster health check").up();

        try {
            Health health = cacheManager.getHealth();
            ClusterHealth clusterHealth = health.getClusterHealth();
            List<CacheHealth> cacheHealths = health.getCacheHealth();

            builder.withData("clusterHealth", clusterHealth.getHealthStatus().toString());
            builder.withData("clusterName", clusterHealth.getClusterName());
            builder.withData("clusterMembers", Integer.toString(clusterHealth.getNumberOfNodes()));

            if (clusterHealth.getHealthStatus() != HealthStatus.HEALTHY) {
                builder.down();
            }

            for (CacheHealth cacheHealth : cacheHealths) {
                String cacheName = cacheHealth.getCacheName();
                builder.withData(cacheName + ".health", cacheHealth.getStatus().toString());
                if (cacheHealth.getStatus() != HealthStatus.HEALTHY) {
                    builder.down();
                }
            }
        } catch (Exception e) {
            builder.down().withData("error", e.getMessage());
        }

        return builder.build();
    }
}

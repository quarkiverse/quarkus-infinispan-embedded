package io.quarkiverse.infinispan.embedded.it.extension;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.infinispan.manager.EmbeddedCacheManager;

import io.quarkus.runtime.StartupEvent;

/**
 * This class is here solely to ensure that if you have more than 1 application scoped bean that references the cache
 * manager that it works properly and doesn't create a new one (the second cache manager would fail to start if not fixed)
 */
@ApplicationScoped
public class EventListener {

    @Inject
    EmbeddedCacheManager cacheManager;

    public void onStartup(@Observes StartupEvent event) {
        // do something on startup
        System.out.println("Starting test application");
    }
}

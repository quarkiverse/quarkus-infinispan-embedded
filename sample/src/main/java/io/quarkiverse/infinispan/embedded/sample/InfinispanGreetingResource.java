package io.quarkiverse.infinispan.embedded.sample;

import java.util.concurrent.CompletionStage;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

import org.infinispan.Cache;
import org.infinispan.commons.api.CacheContainerAdmin;
import org.infinispan.commons.dataconversion.MediaType;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.EmbeddedCacheManager;

import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;

@Path("/greeting")
public class InfinispanGreetingResource {

    public static final String CACHE_NAME = "mycache";

    @Inject
    EmbeddedCacheManager cacheManager;

    @Startup
    void init() {
        Configuration config = new ConfigurationBuilder()
                .encoding().mediaType(MediaType.APPLICATION_PROTOSTREAM)
                .clustering().cacheMode(CacheMode.DIST_ASYNC).build();
        Log.info("Create mycache with config " + config);
        cacheManager.administration()
                .withFlags(CacheContainerAdmin.AdminFlag.VOLATILE)
                .getOrCreateCache(CACHE_NAME, config);
    }

    @POST
    @Path("/{id}")
    public CompletionStage<String> postGreeting(String id, Greeting greeting) {
        Cache<String, Greeting> cache = cacheManager.getCache(CACHE_NAME);
        return cache.putAsync(id, greeting)
                .thenApply(g -> "Greeting added!")
                .exceptionally(ex -> ex.getMessage());
    }

    @GET
    @Path("/{id}")
    public CompletionStage<Greeting> getGreeting(String id) {
        Cache<String, Greeting> cache = cacheManager.getCache(CACHE_NAME);
        return cache.getAsync(id);
    }
}

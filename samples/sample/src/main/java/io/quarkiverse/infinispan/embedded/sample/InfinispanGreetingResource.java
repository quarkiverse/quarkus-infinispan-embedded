package io.quarkiverse.infinispan.embedded.sample;

import java.util.concurrent.CompletionStage;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

import org.infinispan.Cache;

import io.quarkiverse.infinispan.embedded.Embedded;

@Path("/greeting")
public class InfinispanGreetingResource {
    public static final String CACHE_NAME = "mycache";

    @Inject
    @Embedded(CACHE_NAME)
    Cache<String, Greeting> cache;

    @POST
    @Path("/{id}")
    public CompletionStage<String> postGreeting(String id, Greeting greeting) {
        return cache.putAsync(id, greeting)
                .thenApply(g -> "Greeting added!")
                .exceptionally(ex -> ex.getMessage());
    }

    @GET
    @Path("/{id}")
    public CompletionStage<Greeting> getGreeting(String id) {
        return cache.getAsync(id);
    }
}

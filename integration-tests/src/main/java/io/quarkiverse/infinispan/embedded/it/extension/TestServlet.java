package io.quarkiverse.infinispan.embedded.it.extension;

import static org.infinispan.commons.dataconversion.MediaType.APPLICATION_PROTOSTREAM;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.infinispan.Cache;
import org.infinispan.commons.api.CacheContainerAdmin;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;

@Path("/test")
public class TestServlet {
    @Inject
    EmbeddedCacheManager emc;

    @Inject
    ReviewService reviewService;

    // Having on start method will eagerly initialize the cache manager which in turn starts up clustered cache
    void onStart(@Observes StartupEvent ev) {
        Log.info("The application is starting...");
    }

    @Path("GET/{cacheName}/{id}")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String get(@PathParam("cacheName") String cacheName, @PathParam("id") String id) {
        Log.info("Retrieving " + id + " from " + cacheName);
        Cache<byte[], byte[]> cache = emc.getCache(cacheName);
        byte[] result = cache.get(id.getBytes(StandardCharsets.UTF_8));
        return result == null ? "null" : new String(result, StandardCharsets.UTF_8);
    }

    @Transactional
    @Path("PUT/{cacheName}/{id}/{value}")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String put(@PathParam("cacheName") String cacheName, @PathParam("id") String id, @PathParam("value") String value,
            @QueryParam("shouldFail") String shouldFail) {
        Log.info("Putting " + id + " with value: " + value + " into " + cacheName);
        Cache<byte[], byte[]> cache = emc.getCache(cacheName);
        byte[] result = cache.put(id.getBytes(StandardCharsets.UTF_8), value.getBytes(StandardCharsets.UTF_8));
        if (Boolean.parseBoolean(shouldFail)) {
            throw new RuntimeException("Forced Exception!");
        }
        return result == null ? "null" : new String(result, StandardCharsets.UTF_8);
    }

    @Path("REMOVE/{cacheName}/{id}")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String remove(@PathParam("cacheName") String cacheName, @PathParam("id") String id) {
        Log.info("Removing " + id + " from " + cacheName);
        Cache<byte[], byte[]> cache = emc.getCache(cacheName);
        byte[] result = cache.remove(id.getBytes(StandardCharsets.UTF_8));
        return result == null ? "null" : new String(result, StandardCharsets.UTF_8);
    }

    @Path("CLUSTER")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String simpleCluster() throws IOException {
        ConfigurationBuilder configurationBuilder = new ConfigurationBuilder();
        configurationBuilder.clustering().cacheMode(CacheMode.DIST_SYNC);

        List<EmbeddedCacheManager> managers = new ArrayList<>(3);
        try {
            // Force TCP to connect to loopback, which our TCPPING in dist.xml connects to for discovery
            String oldProperty = System.setProperty("jgroups.tcp.address", "127.0.0.1");
            for (int i = 0; i < 3; i++) {
                EmbeddedCacheManager ecm = new DefaultCacheManager("dist.xml");
                ecm.start();
                managers.add(ecm);
                // Start the default cache
                ecm.getCache();
            }

            if (oldProperty != null) {
                System.setProperty("jgroups.tcp.address", oldProperty);
            }

            long failureTime = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);

            int sizeMatched = 0;
            while (sizeMatched < 3) {
                // reset the size every time
                sizeMatched = 0;
                for (EmbeddedCacheManager ecm : managers) {
                    int size = ecm.getMembers().size();
                    if (size == 3) {
                        sizeMatched++;
                    }
                }
                if (failureTime - System.nanoTime() < 0) {
                    return "Timed out waiting for caches to have joined together!";
                }
            }
        } finally {
            managers.forEach(EmbeddedCacheManager::stop);
        }
        return "Success";
    }

    @Path("PROTO/GET/books/{id}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Book get(@PathParam("id") String id) {
        Cache<String, Book> books = emc.administration()
                .withFlags(CacheContainerAdmin.AdminFlag.VOLATILE)
                .getOrCreateCache("books", new ConfigurationBuilder()
                        .encoding().mediaType(APPLICATION_PROTOSTREAM)
                        .build());
        return books.get(id);
    }

    @Path("PROTO/POST/books/{id}")
    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public String create(@PathParam("id") String id, Book book) {
        Cache<String, Book> books = emc.administration()
                .withFlags(CacheContainerAdmin.AdminFlag.VOLATILE)
                .getOrCreateCache("books", new ConfigurationBuilder()
                        .encoding().mediaType(APPLICATION_PROTOSTREAM)
                        .build());
        books.put(id, book);
        return id;
    }

    @Path("ANNOTATIONS/GET/review/{bookId}")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String create(@PathParam("bookId") String bookId) {
        Objects.requireNonNull(bookId);
        return reviewService.getReview(bookId);
    }

    @Path("ANNOTATIONS/GET/clear/{bookId}")
    @GET
    public void clear(@PathParam("bookId") String bookId) {
        Objects.requireNonNull(bookId);
        if (bookId.equals("all")) {
            reviewService.invalidateAll();
            return;
        }

        reviewService.invalidateReview(bookId);
    }

    @Path("ANNOTATIONS/GET/calls")
    @GET
    public int calls() {
        return reviewService.getCalls();
    }
}

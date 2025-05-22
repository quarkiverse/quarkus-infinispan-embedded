package io.quarkiverse.infinispan.embedded.runtime.devui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.infinispan.AdvancedCache;
import org.infinispan.Cache;
import org.infinispan.manager.EmbeddedCacheManager;

import io.smallrye.common.annotation.NonBlocking;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@ApplicationScoped
public class InfinispanEmbeddedCacheJsonRPCService {

    @Inject
    EmbeddedCacheManager manager;

    @NonBlocking
    public JsonArray getAll() {
        Collection<String> names = manager.getCacheNames();
        List<AdvancedCache> allCaches = new ArrayList<>(names.size());
        for (String name : names) {
            Cache<Object, Object> cache = manager.getCache(name);
            if (cache != null) {
                allCaches.add(cache.getAdvancedCache());
            }
        }
        allCaches.sort(Comparator.comparing(AdvancedCache::getName));

        var array = new JsonArray();
        for (AdvancedCache cc : allCaches) {
            array.add(getJsonRepresentationForCache(cc));
        }
        return array;
    }

    private JsonObject getJsonRepresentationForCache(AdvancedCache cc) {
        if (cc != null) {
            return new JsonObject().put("name", cc.getName()).put("size", cc.size());
        }

        return new JsonObject().put("name", "unknown").put("size", -1);
    }

    public Uni<JsonObject> clear(String name) {
        Cache cache = manager.getCache(name);
        AdvancedCache advancedCache = getAdvancedCache(name);
        if (cache != null) {
            return Uni.createFrom()
                    .completionStage(cache.clearAsync().thenApply((t) -> getJsonRepresentationForCache(advancedCache)));
        } else {
            return Uni.createFrom().item(new JsonObject().put("name", name).put("size", -1));
        }
    }

    @NonBlocking
    public JsonObject refresh(String name) {
        AdvancedCache advancedCache = getAdvancedCache(name);
        if (advancedCache != null) {
            return getJsonRepresentationForCache(advancedCache);
        } else {
            return new JsonObject().put("name", name).put("size", -1);
        }
    }

    public JsonArray getKeys(String name) {
        AdvancedCache advancedCache = getAdvancedCache(name);
        if (advancedCache != null) {
            JsonArray keys = new JsonArray();
            for (Object key : advancedCache.keySet()) {
                keys.add(key.toString());
            }
            return keys;
        } else {
            return JsonArray.of();
        }
    }

    private AdvancedCache getAdvancedCache(String name) {
        Cache cache = manager.getCache(name);
        if (cache != null) {
            return cache.getAdvancedCache();
        }
        return null;
    }

}

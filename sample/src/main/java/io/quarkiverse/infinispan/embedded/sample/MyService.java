package io.quarkiverse.infinispan.embedded.sample;

import jakarta.inject.Inject;

import org.infinispan.Cache;
import org.infinispan.commons.api.CacheContainerAdmin;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.EmbeddedCacheManager;

import io.quarkus.logging.Log;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain
public class MyService implements QuarkusApplication {

    @Inject
    private EmbeddedCacheManager cacheManager;

    @Override
    public int run(String... args) {
        Configuration config = new ConfigurationBuilder()
                .clustering().cacheMode(CacheMode.DIST_ASYNC).build();
        Log.info(cacheManager.administration()
                .withFlags(CacheContainerAdmin.AdminFlag.VOLATILE)
                .createCache("mycache", config));
        Cache<String, String> mycache = cacheManager.getCache("mycache");
        mycache.put("greeting", "Hello world!");
        Log.info(mycache.get("greeting"));
        return 0;
    }
}

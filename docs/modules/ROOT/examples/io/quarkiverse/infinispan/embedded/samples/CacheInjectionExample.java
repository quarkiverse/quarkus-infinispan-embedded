package io.quarkiverse.infinispan.embedded.samples;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.infinispan.Cache;

import io.quarkiverse.infinispan.embedded.Embedded;

@ApplicationScoped
public class CacheInjectionExample {

    @Inject // <1>
    @Embedded("mycache") // <2>
    private Cache<String, String> cache;

    public void addValue(String key, String value) {
        cache.put("greeting", "Hello world!"); // <3>
    }
}

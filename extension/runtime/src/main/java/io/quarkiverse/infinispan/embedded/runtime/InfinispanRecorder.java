package io.quarkiverse.infinispan.embedded.runtime;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import org.infinispan.AdvancedCache;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.protostream.SerializationContextInitializer;
import org.jboss.logging.Logger;

import io.quarkiverse.infinispan.embedded.runtime.cache.InfinispanCacheImpl;
import io.quarkiverse.infinispan.embedded.runtime.cache.InfinispanCacheInfo;
import io.quarkiverse.infinispan.embedded.runtime.cache.InfinispanCacheInfoBuilder;
import io.quarkiverse.infinispan.embedded.runtime.cache.InfinispanCachesConfig;
import io.quarkus.arc.Arc;
import io.quarkus.arc.runtime.BeanContainerListener;
import io.quarkus.cache.Cache;
import io.quarkus.cache.CacheManager;
import io.quarkus.cache.CacheManagerInfo;
import io.quarkus.cache.runtime.CacheManagerImpl;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.runtime.annotations.RelaxedValidation;

@Recorder
public class InfinispanRecorder {
    private static final Logger Log = Logger.getLogger(InfinispanRecorder.class);
    private final RuntimeValue<InfinispanCachesConfig> infinispanCacheConfigRV;

    public InfinispanRecorder(RuntimeValue<InfinispanCachesConfig> infinispanCacheConfigRV) {
        this.infinispanCacheConfigRV = infinispanCacheConfigRV;
    }

    public BeanContainerListener configureInfinispan(@RelaxedValidation List<SerializationContextInitializer> initializers) {
        return container -> {
            InfinispanEmbeddedProducer instance = container.beanInstance(InfinispanEmbeddedProducer.class);
            instance.setInitializers(initializers);
        };
    }

    public void configureRuntimeProperties(InfinispanEmbeddedRuntimeConfig infinispanEmbeddedRuntimeConfig) {
        InfinispanEmbeddedProducer iep = Arc.container().instance(InfinispanEmbeddedProducer.class).get();
        iep.setRuntimeConfig(infinispanEmbeddedRuntimeConfig);
    }

    public Supplier<AdvancedCache> infinispanAdvancedCacheSupplier(String cacheName) {
        return new InfinispanEmbeddedSupplier<>(new Function<InfinispanEmbeddedProducer, AdvancedCache>() {
            @Override
            public AdvancedCache apply(InfinispanEmbeddedProducer infinispanEmbeddedProducer) {
                return infinispanEmbeddedProducer.getAdvancedCache(cacheName);
            }
        });
    }

    public Supplier<EmbeddedCacheManager> infinispanEmbeddedSupplier() {
        return new InfinispanEmbeddedSupplier<>(new Function<InfinispanEmbeddedProducer, EmbeddedCacheManager>() {
            @Override
            public EmbeddedCacheManager apply(InfinispanEmbeddedProducer infinispanEmbeddedProducer) {
                return infinispanEmbeddedProducer.manager();
            }
        });
    }

    public CacheManagerInfo getCacheManagerSupplier() {
        return new CacheManagerInfo() {
            @Override
            public boolean supports(Context context) {
                return context.cacheEnabled() && "infinispan".equals(context.cacheType());
            }

            @Override
            @SuppressWarnings({ "rawtypes", "unchecked" })
            public Supplier<CacheManager> get(Context context) {
                return new Supplier<CacheManager>() {
                    @Override
                    public CacheManager get() {
                        Set<InfinispanCacheInfo> cacheInfos = InfinispanCacheInfoBuilder.build(context.cacheNames(),
                                infinispanCacheConfigRV.getValue());
                        if (cacheInfos.isEmpty()) {
                            return new CacheManagerImpl(Collections.emptyMap());
                        } else {
                            // The number of caches is known at build time so we can use fixed initialCapacity and loadFactor for the caches map.
                            Map<String, Cache> caches = new HashMap<>(cacheInfos.size() + 1, 1.0F);
                            for (InfinispanCacheInfo cacheInfo : cacheInfos) {
                                if (Log.isDebugEnabled()) {
                                    Log.debugf(
                                            "Building Infinispan cache [%s] with [lifespan=%s], [maxIdle=%s]",
                                            cacheInfo.name, cacheInfo.lifespan, cacheInfo.maxIdle);
                                }

                                InfinispanCacheImpl cache = new InfinispanCacheImpl(cacheInfo);
                                caches.put(cacheInfo.name, cache);
                            }
                            return new CacheManagerImpl(caches);
                        }
                    }
                };
            }
        };
    }

    /** Helper to lazily create Infinispan clients. */
    static final class InfinispanEmbeddedSupplier<T> implements Supplier<T> {
        private final Function<InfinispanEmbeddedProducer, T> producer;

        InfinispanEmbeddedSupplier(Function<InfinispanEmbeddedProducer, T> producer) {
            this.producer = producer;
        }

        @Override
        public T get() {
            InfinispanEmbeddedProducer infinispanEmbeddedProducer = Arc.container().instance(InfinispanEmbeddedProducer.class)
                    .get();
            return producer.apply(infinispanEmbeddedProducer);
        }
    }
}

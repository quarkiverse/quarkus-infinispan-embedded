package io.quarkiverse.infinispan.embedded.deployment.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.inject.Inject;

import org.infinispan.commons.jdkspecific.ThreadCreator;
import org.infinispan.commons.util.NullValue;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.infinispan.embedded.Embedded;
import io.quarkiverse.infinispan.embedded.runtime.cache.InfinispanCacheImpl;
import io.quarkiverse.infinispan.embedded.runtime.cache.InfinispanCacheInfo;
import io.quarkus.cache.Cache;
import io.quarkus.cache.CompositeCacheKey;
import io.quarkus.logging.Log;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Uni;

public class InfinispanEmbeddedCacheTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withEmptyApplication()
            .withConfigurationResource("empty-application-infinispan-embedded.properties");

    private static final String CACHE_NAME = "cache";

    private static final ThreadFactory defaultThreadFactory = getTestThreadFactory("ForkThread");
    private static final ExecutorService testExecutor = ThreadCreator.createBlockingExecutorService()
            .orElseGet(() -> new ThreadPoolExecutor(0, Integer.MAX_VALUE,
                    60L, TimeUnit.SECONDS,
                    new SynchronousQueue<>(),
                    defaultThreadFactory));
    @Inject
    @Embedded("cache")
    org.infinispan.Cache advancedCache;

    @BeforeEach
    void clear() {
        try {
            advancedCache.clear();
        } catch (Exception ignored) {
            // ignored.
        }
    }

    @AfterAll
    public static void shutdown() {
        testExecutor.shutdown();
    }

    @Test
    public void testGetName() {
        Cache cache = getCacheAbstraction();
        assertThat(cache.getName()).isEqualTo(CACHE_NAME);
    }

    @Test
    public void testGetDefaultKey() {
        Cache cache = getCacheAbstraction();
        assertThat(cache.getDefaultKey()).isEqualTo("default-key");
    }

    @Test
    public void testGetWithLifespan() throws Exception {
        Cache cache = getCacheAbstraction(2, -1);
        String id = generateId();
        String value = awaitUni(cache.get(id, key -> "one"));
        assertThat(value).isEqualTo("one");
        value = awaitUni(cache.get(id, key -> "two"));
        assertThat(value).isEqualTo("one");
        assertThat(advancedCache.get(id)).isEqualTo("one");
        // Wait lifespan expiration
        await().atMost(Duration.ofSeconds(3)).until(() -> advancedCache.get(id) == null);
        // key has expired
        assertThat(advancedCache.get(id)).isNull();
        value = awaitUni(cache.get(id, key -> "two"));
        assertThat(value).isEqualTo("two");
        assertThat(advancedCache.get(id)).isEqualTo("two");
        // Wait lifespan expiration
        await().atMost(Duration.ofSeconds(3)).until(() -> advancedCache.get(id) == null);
        assertThat(advancedCache.get(id)).isNull();
    }

    @Test
    public void testGetWithWithMaxidle() {
        Cache cache = getCacheAbstraction(-1, 3);
        String id = generateId();
        String value = awaitUni(cache.get(id, key -> "one"));
        assertThat(value).isEqualTo("one");
        value = awaitUni(cache.get(id, key -> "two"));
        assertThat(value).isEqualTo("one");
        assertThat(advancedCache.get(id)).isEqualTo("one");
        // Wait maxidle expiration
        await().pollDelay(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(true).isTrue());
        // key has expired
        assertThat(advancedCache.get(id)).isNull();
        value = awaitUni(cache.get(id, key -> "two"));
        assertThat(value).isEqualTo("two");
        assertThat(advancedCache.get(id)).isEqualTo("two");
        // Wait maxidle expiration
        await().pollDelay(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(true).isTrue());
        assertThat(advancedCache.get(id)).isNull();
    }

    @Test
    public void testGetWithNullValues() {
        Cache cache = getCacheAbstraction();
        String id = generateId();
        String value = awaitUni(cache.get(id, key -> null));
        assertThat(value).isEqualTo(null);
        assertThat(advancedCache.get(id)).isEqualTo(NullValue.NULL);
    }

    protected <T> Future<T> fork(Callable<T> c) {
        return testExecutor.submit(new CallableWrapper<>(c));
    }

    private static class CallableWrapper<T> implements Callable<T> {
        private final Callable<? extends T> c;

        CallableWrapper(Callable<? extends T> c) {
            this.c = c;
        }

        @Override
        public T call() throws Exception {
            try {
                Log.trace("Started fork callable..");
                T result = c.call();
                Log.debug("Exiting fork callable.");
                return result;
            } catch (Exception e) {
                Log.warn("Exiting fork callable due to exception", e);
                throw e;
            }
        }
    }

    protected static ThreadFactory getTestThreadFactory(final String prefix) {
        final String className = InfinispanEmbeddedCacheTest.class.getSimpleName();

        return new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                String threadName = prefix + "-" + counter.incrementAndGet() + "," + className;
                return new Thread(r, threadName);
            }
        };
    }

    @Test
    public void testGetWithParallelCalls() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        Cache cache = getCacheAbstraction();
        String id = generateId();
        Future<String> thread1 = fork(() -> cache.get(id, key -> {
            try {
                // In order to avoid it to be a flaky test, first call is to make sure we are inside the lambda.
                // The second to wait inside the lambda until we issue the second request on line 193
                barrier.await(10, TimeUnit.SECONDS);
                barrier.await(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return "thread1";
        }).await().atMost(Duration.ofSeconds(10)));

        // Ensure first retrieval is in lambda before continuing
        barrier.await(10, TimeUnit.SECONDS);

        Future<String> thread2 = fork(() -> cache.get(id, key -> "thread2").await().atMost(Duration.ofSeconds(10)));

        barrier.await(1, TimeUnit.SECONDS);

        String valueObtainedByThread1 = thread1.get(10, TimeUnit.SECONDS);
        String valueObtainedByThread2 = thread2.get(10, TimeUnit.SECONDS);

        assertThat(advancedCache.get(id)).isEqualTo("thread1");
        assertThat(valueObtainedByThread1).isEqualTo("thread1");
        assertThat(valueObtainedByThread2).isEqualTo("thread1");
    }

    @Test
    public void testGetAsyncWithLifespan() {
        Cache cache = getCacheAbstraction(2, -1);
        String id = generateId();
        String value = awaitUni(cache.getAsync(id, key -> Uni.createFrom().item("one")));
        assertThat(value).isEqualTo("one");
        value = awaitUni(cache.getAsync(id, key -> Uni.createFrom().item("two")));
        assertThat(value).isEqualTo("one");
        assertThat(advancedCache.get(id)).isEqualTo("one");
        // Wait lifespan expiration
        await().atMost(Duration.ofSeconds(3)).until(() -> advancedCache.get(id) == null);
        // key has expired
        assertThat(advancedCache.get(id)).isNull();
        value = awaitUni(cache.getAsync(id, key -> Uni.createFrom().item("two")));
        assertThat(value).isEqualTo("two");
        assertThat(advancedCache.get(id)).isEqualTo("two");
        // Wait lifespan expiration
        await().atMost(Duration.ofSeconds(3)).until(() -> advancedCache.get(id) == null);
        assertThat(advancedCache.get(id)).isNull();
    }

    @Test
    public void testGetAsyncWithWithMaxidle() {
        Cache cache = getCacheAbstraction(-1, 3);
        String id = generateId();
        String value = awaitUni(cache.getAsync(id, key -> Uni.createFrom().item("one")));
        assertThat(value).isEqualTo("one");
        value = awaitUni(cache.getAsync(id, key -> Uni.createFrom().item("two")));
        assertThat(value).isEqualTo("one");
        assertThat(advancedCache.get(id)).isEqualTo("one");
        // Wait maxidle expiration
        await().pollDelay(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(true).isTrue());
        // key has expired
        assertThat(advancedCache.get(id)).isNull();
        value = awaitUni(cache.getAsync(id, key -> Uni.createFrom().item("two")));
        assertThat(value).isEqualTo("two");
        assertThat(advancedCache.get(id)).isEqualTo("two");
        // Wait maxidle expiration
        await().pollDelay(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(true).isTrue());
        assertThat(advancedCache.get(id)).isNull();
    }

    @Test
    public void testGetAsyncWithNullValues() {
        Cache cache = getCacheAbstraction();
        String id = generateId();
        String value = awaitUni(cache.getAsync(id, key -> Uni.createFrom().nullItem()));
        assertThat(value).isEqualTo(null);
        assertThat(advancedCache.get(id)).isEqualTo(NullValue.NULL);
    }

    @Test
    public void testGetAsyncWithParallelCalls() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        Cache cache = getCacheAbstraction();
        String id = generateId();
        Future<Uni<String>> thread1 = fork(() -> cache.getAsync(id, key -> {
            try {
                barrier.await(10, TimeUnit.SECONDS);
                barrier.await(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return Uni.createFrom().item("thread1");
        }));

        // Ensure first retrieval is in lambda before continuing
        barrier.await(10, TimeUnit.SECONDS);

        Future<Uni<String>> thread2 = fork(() -> cache.getAsync(id, key -> Uni.createFrom().item("thread2")));

        barrier.await(1, TimeUnit.SECONDS);

        String valueObtainedByThread1 = awaitUni(thread1.get(10, TimeUnit.SECONDS));
        String valueObtainedByThread2 = awaitUni(thread2.get(10, TimeUnit.SECONDS));

        assertThat(advancedCache.get(id)).isEqualTo("thread1");
        assertThat(valueObtainedByThread1).isEqualTo("thread1");
        assertThat(valueObtainedByThread2).isEqualTo("thread1");
    }

    @Test
    public void testInvalidate() {
        Cache cache = getCacheAbstraction();
        String id = generateId();
        awaitUni(cache.get(id, key -> "value"));
        assertThat(advancedCache.size()).isOne();
        assertThat(advancedCache.get(id)).isEqualTo("value");
        awaitUni(cache.invalidate(id));
        assertThat(advancedCache.size()).isZero();
    }

    @Test
    @Disabled
    public void testInvalidateIf() {
        Cache cache = getCacheAbstraction();
        String id1 = generateId();
        String id2 = generateId();
        awaitUni(cache.get(id1, key -> "value"));
        awaitUni(cache.get(id2, key -> null));
        assertThat(advancedCache.get(id1)).isEqualTo("value");
        assertThat(advancedCache.get(id2)).isEqualTo(NullValue.NULL);

        awaitUni(cache.invalidateIf(k -> k.equals(id2)));

        assertThat(advancedCache.containsKey(id1)).isTrue();
        assertThat(advancedCache.containsKey(id2)).isFalse();
    }

    @Test
    public void testInvalidateAll() {
        Cache cache = getCacheAbstraction();
        for (int i = 0; i < 10; i++) {
            awaitUni(cache.get(generateId(), key -> "value"));
        }
        assertThat(advancedCache.size()).isEqualTo(10);
        awaitUni(cache.invalidateAll());
        assertThat(advancedCache.size()).isZero();
    }

    @Test
    public void testGetWithCompositeCacheKey() {
        Cache cache = getCacheAbstraction();
        CompositeCacheKey compositeId = new CompositeCacheKey("id1", "id2");
        awaitUni(cache.get(compositeId, key -> "value"));
        assertThat(advancedCache.get(compositeId)).isEqualTo("value");
    }

    private static String generateId() {
        return UUID.randomUUID().toString();
    }

    private Cache getCacheAbstraction() {
        InfinispanCacheInfo info = new InfinispanCacheInfo();
        info.name = CACHE_NAME;
        info.lifespan = Optional.empty();
        info.maxIdle = Optional.empty();
        return new InfinispanCacheImpl(info, advancedCache);
    }

    private Cache getCacheAbstraction(int lifespan, int maxidle) {
        InfinispanCacheInfo info = new InfinispanCacheInfo();
        info.name = CACHE_NAME;
        info.lifespan = Optional.of(Duration.ofSeconds(lifespan));
        info.maxIdle = Optional.of(Duration.ofSeconds(maxidle));
        return new InfinispanCacheImpl(info, advancedCache);
    }

    private static <T> T awaitUni(Uni<T> uni) {
        return uni.await().atMost(Duration.ofSeconds(10));
    }
}

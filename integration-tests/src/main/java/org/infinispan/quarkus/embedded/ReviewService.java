package org.infinispan.quarkus.embedded;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.cache.CacheInvalidate;
import io.quarkus.cache.CacheInvalidateAll;
import io.quarkus.cache.CacheResult;

@ApplicationScoped
public class ReviewService {
    public static final String REVIEWS = "reviews";
    Random random = new Random();
    AtomicInteger counter = new AtomicInteger(0);

    static final String[] sampleReviews = {
            "Loved it!",
            "Terrible ending.",
            "Would read again.",
            "Not my style.",
            "Masterpiece.",
            "Too predictable.",
            "Excellent pacing.",
            "Boring.",
            "Characters felt real.",
            "Plot twists were amazing!",
            "Fell asleep halfway.",
            "A modern classic.",
            "Dialogue was weak.",
            "Unputdownable!",
            "Didn't live up to the hype.",
            "Heartwarming and emotional.",
            "Way too long.",
            "Beautifully written.",
            "I laughed and cried.",
            "Completely forgettable."
    };

    static final Map<String, String> memoryReviews = initMemoryReviews();

    private static Map<String, String> initMemoryReviews() {
        Map<String, String> memoryReviews = new HashMap<>();
        for (int i = 0; i < sampleReviews.length; i++) {
            String bookId = "BOOK-" + i;
            String review = sampleReviews[i];
            memoryReviews.put(bookId, review);
        }
        return memoryReviews;
    }

    @CacheResult(cacheName = REVIEWS)
    public String getReview(String bookId) {
        counter.incrementAndGet();
        if (memoryReviews.containsKey(bookId)) {
            return memoryReviews.get(bookId);
        }
        return sampleReviews[random.nextInt(sampleReviews.length)];
    }

    @CacheInvalidate(cacheName = REVIEWS)
    public void invalidateReview(String bookId) {

    }

    @CacheInvalidateAll(cacheName = REVIEWS)
    public void invalidateAll() {

    }

    public int getCalls() {
        return counter.get();
    }
}

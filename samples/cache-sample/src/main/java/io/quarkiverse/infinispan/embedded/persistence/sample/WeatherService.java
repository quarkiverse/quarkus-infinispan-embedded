package io.quarkiverse.infinispan.embedded.persistence.sample;

import static java.util.Objects.requireNonNull;

import java.util.concurrent.ThreadLocalRandom;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.cache.CacheInvalidate;
import io.quarkus.cache.CacheInvalidateAll;
import io.quarkus.cache.CacheResult;
import io.quarkus.logging.Log;

@ApplicationScoped
public class WeatherService {
    ThreadLocalRandom random = ThreadLocalRandom.current();

    @CacheResult(cacheName = "weather")
    public Weather retrieve(String city) {
        requireNonNull(city);
        String temperature = String.format("%.1f", random.nextDouble() * 40); // Random temperature between 0 and 40
        String windSpeed = String.format("%dkm", random.nextInt(30) + 10); // Random wind speed between 10 and 40 km/h
        String[] directions = { "North", "South", "East", "West", "Northwest", "Southeast" };
        String windDirection = directions[random.nextInt(directions.length)]; // Random wind direction
        return new Weather(temperature, windSpeed, windDirection);
    }

    @CacheInvalidateAll(cacheName = "weather")
    public void clearAll() {
        Log.info("Clearing weather cache");
    }

    @CacheInvalidate(cacheName = "weather")
    public void clearWeather(String city) {
        Log.info("Clear city: " + city);
    }
}

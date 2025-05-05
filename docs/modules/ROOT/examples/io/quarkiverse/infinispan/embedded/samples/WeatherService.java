package io.quarkiverse.infinispan.embedded.samples;

import static java.util.Objects.requireNonNull;

import java.util.concurrent.ThreadLocalRandom;

import jakarta.enterprise.context.ApplicationScoped;

import org.infinispan.protostream.GeneratedSchema;
import org.infinispan.protostream.annotations.Proto;
import org.infinispan.protostream.annotations.ProtoSchema;

import io.quarkus.cache.CacheInvalidate;
import io.quarkus.cache.CacheInvalidateAll;
import io.quarkus.cache.CacheResult;
import io.quarkus.logging.Log;

@ApplicationScoped // <1>
public class WeatherService {
    ThreadLocalRandom random = ThreadLocalRandom.current();

    @Proto // <2>
    public record Weather(String temperature, String windSpeed, String windDirection) {
        @ProtoSchema(includeClasses = { Weather.class }, // <3>
                schemaPackageName = "io.quarkiverse.weather")
        public interface WeatherSchema extends GeneratedSchema {
        }
    }

    @CacheResult(cacheName = "weather") // <4>
    public Weather retrieve(String city) {
        requireNonNull(city);
        String temperature = String.format("%.1f", random.nextDouble() * 40); // Random temperature between 0 and 40
        String windSpeed = String.format("%dkm", random.nextInt(30) + 10); // Random wind speed between 10 and 40 km/h
        String[] directions = { "North", "South", "East", "West", "Northwest", "Southeast" };
        String windDirection = directions[random.nextInt(directions.length)]; // Random wind direction
        return new Weather(temperature, windSpeed, windDirection);
    }

    @CacheInvalidateAll(cacheName = "weather") // <5>
    public void clearAll() {
        Log.info("Clearing weather cache");
    }

    @CacheInvalidate(cacheName = "weather") // <6>
    public void clearWeather(String city) {
        Log.info("Clear city: " + city);
    }
}

package io.quarkiverse.infinispan.embedded.persistence.sample;

import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("/weather")
public class WeatherResource {
    @Inject
    WeatherService service;

    @GET
    @Path("/{city}")
    public Weather getWeather(String city) {
        return service.retrieve(city.toUpperCase());
    }

    @DELETE
    @Path("/clear")
    public void clearWeather(String city) {
        service.clearWeather(city.toUpperCase());
    }

    @DELETE
    @Path("/restart")
    public void restart() {
        service.clearAll();
    }
}

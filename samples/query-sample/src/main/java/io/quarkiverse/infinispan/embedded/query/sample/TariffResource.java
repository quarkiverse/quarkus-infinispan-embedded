package io.quarkiverse.infinispan.embedded.query.sample;

import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;

import org.infinispan.Cache;
import org.infinispan.commons.api.query.Query;

import io.quarkiverse.infinispan.embedded.Embedded;

@Path("/tariff")
public class TariffResource {

    @Inject
    @Embedded("queryCache")
    Cache<String, Tariff> tariffCache;

    @POST
    public Response createTariff(Tariff tariff) {
        tariffCache.put(UUID.randomUUID().toString(), tariff);
        return Response.ok().build();
    }

    @GET
    @Path("/{id}")
    public Response createTariff(@PathParam("id") Integer id) {
        Query<Tariff> query = tariffCache.query("from io.quarkiverse.infinispan.embedded.query.sample.Tariff where id=" + id);
        return Response.ok().entity(query.execute().list()).build();
    }

}

package io.quarkiverse.infinispan.embedded.it.cache;

import jakarta.inject.Inject;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.ext.Provider;

@Provider
public class CustomClientRequestFilter implements ClientRequestFilter {

    ClientRequestService requestService;

    @Inject
    public CustomClientRequestFilter(ClientRequestService requestService) {
        this.requestService = requestService;
    }

    @Override
    public void filter(ClientRequestContext requestContext) {
        if (requestService != null && requestService.data() != null) {
            requestContext.getHeaders().add("extra", requestService.data());
        }
    }
}
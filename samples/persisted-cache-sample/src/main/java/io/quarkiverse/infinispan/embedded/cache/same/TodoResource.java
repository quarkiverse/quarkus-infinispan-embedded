package io.quarkiverse.infinispan.embedded.cache.same;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

@Path("/todolist")
public class TodoResource {
    @Inject
    TodoListService service;

    @GET
    public List<TodoItem> list() {
        return service.list();
    }

    @POST
    public List<TodoItem> add(TodoItem item) {
        service.add(item);
        return list();
    }

    @DELETE
    @Path("/{id}")
    public List<TodoItem> delete(String id) {
        service.delete(id);
        return list();
    }
}

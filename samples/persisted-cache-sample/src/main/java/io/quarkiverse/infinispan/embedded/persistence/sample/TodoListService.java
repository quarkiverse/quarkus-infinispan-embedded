package io.quarkiverse.infinispan.embedded.persistence.sample;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.infinispan.Cache;

import io.quarkiverse.infinispan.embedded.Embedded;

@ApplicationScoped
public class TodoListService {

    @Inject
    @Embedded("todolist")
    Cache<String, TodoItem> todoItemCache;

    public List<TodoItem> list() {
        return todoItemCache.values().stream().sorted(Comparator.comparing(TodoItem::title)).toList();
    }

    public void add(TodoItem item) {
        if (item.id() == null) {
            String id = UUID.randomUUID().toString();
            todoItemCache.put(id, new TodoItem(id, item.title(), item.description(), item.critical()));
        } else {
            todoItemCache.putIfAbsent(item.id(), item);
        }
    }

    public void delete(String id) {
        todoItemCache.remove(id);
    }
}

package io.quarkiverse.infinispan.embedded.persistence.sample;

import org.infinispan.protostream.GeneratedSchema;
import org.infinispan.protostream.annotations.Proto;
import org.infinispan.protostream.annotations.ProtoSchema;

@Proto
public record TodoItem(String id, String title, String description, boolean critical) {

    @ProtoSchema(includeClasses = { TodoItem.class }, schemaPackageName = "io.quarkiverse.todolist")
    public interface TodoSchema extends GeneratedSchema {
    }
}

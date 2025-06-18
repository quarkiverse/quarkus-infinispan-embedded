package io.quarkiverse.infinispan.embedded.it.extension;

import org.infinispan.protostream.GeneratedSchema;
import org.infinispan.protostream.annotations.Proto;
import org.infinispan.protostream.annotations.ProtoSchema;

@Proto
public record Book(String name, String author) {

    @ProtoSchema(includeClasses = Book.class, schemaPackageName = "it.quarkus")
    public interface BookSchema extends GeneratedSchema {

    }
}

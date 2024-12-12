package io.quarkiverse.infinispan.embedded.sample;

import org.infinispan.protostream.GeneratedSchema;
import org.infinispan.protostream.annotations.Proto;
import org.infinispan.protostream.annotations.ProtoSchema;

@Proto
public record Greeting(String name, String message) {
    @ProtoSchema(includeClasses = { Greeting.class }, schemaPackageName = "io.quarkiverse.infinispan")
    public interface GameSchema extends GeneratedSchema {
    }
}

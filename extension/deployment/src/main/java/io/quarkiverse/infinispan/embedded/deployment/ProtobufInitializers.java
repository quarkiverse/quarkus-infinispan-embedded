package io.quarkiverse.infinispan.embedded.deployment;

import java.util.List;

import org.infinispan.protostream.SerializationContextInitializer;

import io.quarkus.builder.item.SimpleBuildItem;

public final class ProtobufInitializers extends SimpleBuildItem {

    private final List<SerializationContextInitializer> initializers;

    public ProtobufInitializers(List<SerializationContextInitializer> initializers) {
        this.initializers = initializers;
    }

    public List<SerializationContextInitializer> getInitializers() {
        return initializers;
    }
}

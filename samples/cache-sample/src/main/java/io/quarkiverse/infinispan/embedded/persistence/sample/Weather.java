package io.quarkiverse.infinispan.embedded.persistence.sample;

import org.infinispan.protostream.GeneratedSchema;
import org.infinispan.protostream.annotations.Proto;
import org.infinispan.protostream.annotations.ProtoSchema;

@Proto
public record Weather(String temperature, String windSpeed, String windDirection) {
    @ProtoSchema(includeClasses = { Weather.class }, schemaPackageName = "io.quarkiverse.weather")
    public interface WeatherSchema extends GeneratedSchema {
    }
}

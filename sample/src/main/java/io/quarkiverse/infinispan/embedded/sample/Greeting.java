package io.quarkiverse.infinispan.embedded.sample;

import org.infinispan.protostream.annotations.Proto;

@Proto
public record Greeting(String name, String message) { }

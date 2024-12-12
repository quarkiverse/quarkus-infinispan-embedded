package io.quarkiverse.infinispan.embedded.runtime;

public class InfinispanEmbeddedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public InfinispanEmbeddedException(String message, Throwable cause) {
        super(message, cause);
    }
}

package io.quarkiverse.infinispan.embedded.runtime;

import static org.infinispan.protostream.FileDescriptorSource.fromString;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;

import org.infinispan.AdvancedCache;
import org.infinispan.Cache;
import org.infinispan.commons.CacheConfigurationException;
import org.infinispan.commons.api.CacheContainerAdmin;
import org.infinispan.commons.configuration.attributes.Attribute;
import org.infinispan.commons.configuration.attributes.AttributeSet;
import org.infinispan.commons.configuration.io.ConfigurationResourceResolvers;
import org.infinispan.commons.dataconversion.MediaType;
import org.infinispan.commons.tx.lookup.TransactionManagerLookup;
import org.infinispan.commons.util.FileLookupFactory;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.cache.TransactionConfiguration;
import org.infinispan.configuration.cache.TransactionConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.configuration.parsing.ConfigurationBuilderHolder;
import org.infinispan.configuration.parsing.ParserRegistry;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.protostream.SerializationContext;
import org.infinispan.protostream.SerializationContextInitializer;
import org.infinispan.protostream.schema.Schema;
import org.infinispan.protostream.schema.Type;
import org.infinispan.transaction.lookup.JBossStandaloneJTAManagerLookup;
import org.jboss.logging.Logger;

import io.quarkiverse.infinispan.embedded.runtime.cache.CompositeCacheKeyMarshaller;
import io.quarkus.arc.Arc;

@ApplicationScoped
public class InfinispanEmbeddedProducer {
    private static final Logger Log = Logger.getLogger(InfinispanEmbeddedProducer.class);
    public static final String QUARKUS_LOCAL_CACHE_CONFIGURATION_NAME = "DEFAULT_LOCAL_QUARKUS_CACHE_CONFIGURATION";
    public static final String QUARKUS_CLUSTERED_CACHE_CONFIGURATION_NAME = "DEFAULT_CLUSTERED_QUARKUS_CACHE_CONFIGURATION";
    private static final Configuration DEFAULT_LOCAL_QUARKUS_CACHE_CONFIGURATION = new ConfigurationBuilder()
            .clustering().cacheMode(CacheMode.LOCAL)
            .build();
    private static final Configuration DEFAULT_DIST_QUARKUS_CACHE_CONFIGURATION = new ConfigurationBuilder()
            .clustering().cacheMode(CacheMode.DIST_SYNC)
            .encoding()
            .mediaType(MediaType.APPLICATION_PROTOSTREAM)
            .build();
    public static final String DEFAULT_CACHE_NAME = "default";
    private volatile InfinispanEmbeddedRuntimeConfig config;
    private List<SerializationContextInitializer> initializers;

    public void setRuntimeConfig(InfinispanEmbeddedRuntimeConfig config) {
        this.config = config;
    }

    EmbeddedCacheManager manager() {
        DefaultCacheManager defaultCacheManager;
        QuarkusContextInitializer quarkusContextInitializer = new QuarkusContextInitializer();
        if (config.xmlConfig().isPresent()) {
            String configurationFile = config.xmlConfig().get();
            try {
                InputStream configurationStream = FileLookupFactory.newInstance().lookupFileStrict(configurationFile,
                        Thread.currentThread().getContextClassLoader());
                ConfigurationBuilderHolder configHolder = new ParserRegistry().parse(configurationStream,
                        ConfigurationResourceResolvers.DEFAULT, MediaType.APPLICATION_XML);
                ConfigurationBuilder defaultConfigurationBuilder = configHolder.getDefaultConfigurationBuilder();
                verifyTransactionConfiguration(defaultConfigurationBuilder, DEFAULT_CACHE_NAME);
                for (Map.Entry<String, ConfigurationBuilder> entry : configHolder.getNamedConfigurationBuilders().entrySet()) {
                    verifyTransactionConfiguration(entry.getValue(), entry.getKey());
                }
                configHolder.getGlobalConfigurationBuilder().serialization().addContextInitializers(initializers);
                configHolder.getGlobalConfigurationBuilder().serialization().addContextInitializer(quarkusContextInitializer);
                defaultCacheManager = new DefaultCacheManager(configHolder, true);
            } catch (IOException e) {
                Log.error(e);
                throw new InfinispanEmbeddedException(
                        String.format("Unable to create the EmbeddedCacheManager with the xmlConfig %s", configurationFile), e);
            }
        } else {
            GlobalConfigurationBuilder builder;
            if (config.clustered()) {
                builder = GlobalConfigurationBuilder.defaultClusteredBuilder();
            } else {
                builder = new GlobalConfigurationBuilder();
            }

            builder.serialization().addContextInitializer(quarkusContextInitializer);
            builder.serialization().addContextInitializers(initializers);
            defaultCacheManager = new DefaultCacheManager(builder.build());
        }

        defaultCacheManager
                .administration()
                .withFlags(CacheContainerAdmin.AdminFlag.VOLATILE)
                .getOrCreateTemplate(QUARKUS_LOCAL_CACHE_CONFIGURATION_NAME, DEFAULT_LOCAL_QUARKUS_CACHE_CONFIGURATION);
        defaultCacheManager
                .administration()
                .withFlags(CacheContainerAdmin.AdminFlag.VOLATILE)
                .getOrCreateTemplate(QUARKUS_CLUSTERED_CACHE_CONFIGURATION_NAME, DEFAULT_DIST_QUARKUS_CACHE_CONFIGURATION);
        return defaultCacheManager;
    }

    public void setInitializers(List<SerializationContextInitializer> initializers) {
        this.initializers = initializers;
    }

    private static class QuarkusContextInitializer implements SerializationContextInitializer {
        static Schema schema = new Schema.Builder("io.quarkus.cache.infinispan.internal.cache.proto")
                .packageName(CompositeCacheKeyMarshaller.PACKAGE)
                .addImport("org/infinispan/protostream/message-wrapping.proto")
                .addMessage(CompositeCacheKeyMarshaller.NAME)
                .addRepeatedField(Type.create("org.infinispan.protostream.WrappedMessage"), CompositeCacheKeyMarshaller.KEYS, 1)
                .build();

        QuarkusContextInitializer() {

        }

        @Override
        public String getProtoFileName() {
            return schema.getName();
        }

        @Override
        public String getProtoFile() throws UncheckedIOException {
            return schema.toString();
        }

        @Override
        public void registerSchema(SerializationContext serCtx) {
            serCtx.registerProtoFiles(fromString(schema.getName(), schema.toString()));
        }

        @Override
        public void registerMarshallers(SerializationContext serCtx) {
            serCtx.registerMarshaller(new CompositeCacheKeyMarshaller());
        }
    }

    /**
     * Verifies that if a configuration has transactions enabled that it only uses the lookup that uses the
     * JBossStandaloneJTAManager, which looks up the transaction manager used by Quarkus
     *
     * @param configurationBuilder the current configuration
     * @param cacheName the cache for the configuration
     */
    private void verifyTransactionConfiguration(ConfigurationBuilder configurationBuilder, String cacheName) {
        if (configurationBuilder == null) {
            return;
        }

        TransactionConfigurationBuilder transactionConfigurationBuilder = configurationBuilder.transaction();
        if (transactionConfigurationBuilder.transactionMode() != null
                && transactionConfigurationBuilder.transactionMode().isTransactional()) {
            AttributeSet attributes = transactionConfigurationBuilder.attributes();
            Attribute<TransactionManagerLookup> managerLookup = attributes
                    .attribute(TransactionConfiguration.TRANSACTION_MANAGER_LOOKUP);
            if (managerLookup.isModified() && !(managerLookup.get() instanceof JBossStandaloneJTAManagerLookup)) {
                throw new CacheConfigurationException(
                        "Only JBossStandaloneJTAManagerLookup transaction manager lookup is supported. Cache " + cacheName
                                + " is misconfigured!");
            }
            managerLookup.set(new JBossStandaloneJTAManagerLookup());
        }
    }

    public Cache getCache(String cacheName) {
        EmbeddedCacheManager cacheManager = Arc.container().instance(EmbeddedCacheManager.class, Default.Literal.INSTANCE)
                .get();
        if (cacheManager.cacheExists(cacheName)) {
            return cacheManager.getCache(cacheName).getAdvancedCache();
        }

        String defaultConfig = QUARKUS_LOCAL_CACHE_CONFIGURATION_NAME;
        if (cacheManager.getCacheManagerConfiguration().isClustered()) {
            defaultConfig = QUARKUS_CLUSTERED_CACHE_CONFIGURATION_NAME;
        }

        return cacheManager.administration()
                .withFlags(CacheContainerAdmin.AdminFlag.VOLATILE)
                .getOrCreateCache(cacheName, defaultConfig);
    }

    public AdvancedCache getAdvancedCache(String cacheName) {
        return getCache(cacheName).getAdvancedCache();
    }
}

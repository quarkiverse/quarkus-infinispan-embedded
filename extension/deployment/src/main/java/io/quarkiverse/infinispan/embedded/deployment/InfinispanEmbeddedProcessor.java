package io.quarkiverse.infinispan.embedded.deployment;

import static io.quarkus.deployment.annotations.ExecutionTime.RUNTIME_INIT;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;

import org.infinispan.AdvancedCache;
import org.infinispan.commons.marshall.AbstractExternalizer;
import org.infinispan.commons.marshall.AdvancedExternalizer;
import org.infinispan.configuration.cache.AbstractModuleConfigurationBuilder;
import org.infinispan.configuration.cache.StoreConfiguration;
import org.infinispan.configuration.cache.StoreConfigurationBuilder;
import org.infinispan.configuration.parsing.ConfigurationParser;
import org.infinispan.configuration.serializing.ConfigurationSerializer;
import org.infinispan.factories.impl.ModuleMetadataBuilder;
import org.infinispan.health.CacheHealth;
import org.infinispan.health.ClusterHealth;
import org.infinispan.interceptors.AsyncInterceptor;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.notifications.Listener;
import org.infinispan.persistence.spi.CacheWriter;
import org.infinispan.persistence.spi.NonBlockingStore;
import org.infinispan.protostream.BaseMarshaller;
import org.infinispan.protostream.EnumMarshaller;
import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.GeneratedSchema;
import org.infinispan.protostream.MessageMarshaller;
import org.infinispan.protostream.SerializationContextInitializer;
import org.infinispan.protostream.WrappedMessage;
import org.infinispan.protostream.schema.Schema;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Type;

import com.github.benmanes.caffeine.cache.CacheLoader;

import io.quarkiverse.infinispan.embedded.Embedded;
import io.quarkiverse.infinispan.embedded.runtime.InfinispanEmbeddedProducer;
import io.quarkiverse.infinispan.embedded.runtime.InfinispanEmbeddedRuntimeConfig;
import io.quarkiverse.infinispan.embedded.runtime.InfinispanRecorder;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.BeanContainerListenerBuildItem;
import io.quarkus.arc.deployment.BeanDiscoveryFinishedBuildItem;
import io.quarkus.arc.deployment.BeanRegistrationPhaseBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.cache.CompositeCacheKey;
import io.quarkus.cache.deployment.spi.CacheManagerInfoBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.ApplicationIndexBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ServiceProviderBuildItem;

class InfinispanEmbeddedProcessor {

    private static final DotName INFINISPAN_EMBEDDED_ANNOTATION = DotName.createSimple(Embedded.class.getName());
    private static final String FEATURE = "infinispan-embedded";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void addInfinispanDependencies(BuildProducer<IndexDependencyBuildItem> indexDependency) {
        indexDependency.produce(new IndexDependencyBuildItem("org.jgroups", "jgroups"));
        indexDependency.produce(new IndexDependencyBuildItem("org.jgroups", "jgroups-raft"));
        indexDependency.produce(new IndexDependencyBuildItem("org.infinispan", "infinispan-commons"));
        indexDependency.produce(new IndexDependencyBuildItem("org.infinispan", "infinispan-core"));
        indexDependency.produce(new IndexDependencyBuildItem("org.infinispan", "infinispan-cachestore-jdbc-common"));
        indexDependency.produce(new IndexDependencyBuildItem("org.infinispan", "infinispan-cachestore-jdbc"));
        indexDependency.produce(new IndexDependencyBuildItem("org.infinispan", "infinispan-cachestore-sql"));
        indexDependency.produce(new IndexDependencyBuildItem("org.infinispan", "infinispan-query"));
        indexDependency.produce(new IndexDependencyBuildItem("org.infinispan", "infinispan-query-core"));
    }

    @BuildStep
    ProtobufInitializers setup(BuildProducer<ReflectiveClassBuildItem> reflectiveClass,
            BuildProducer<ServiceProviderBuildItem> serviceProvider, BuildProducer<AdditionalBeanBuildItem> additionalBeans,
            BuildProducer<NativeImageResourceBuildItem> resources, CombinedIndexBuildItem combinedIndexBuildItem,
            List<InfinispanReflectionExcludedBuildItem> excludedReflectionClasses,
            ApplicationIndexBuildItem applicationIndexBuildItem) {

        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(InfinispanEmbeddedProducer.class));
        additionalBeans.produce(AdditionalBeanBuildItem.builder().addBeanClass(Embedded.class).build());

        resources.produce(new NativeImageResourceBuildItem("proto/generated/persistence.query.core.proto"));
        resources.produce(new NativeImageResourceBuildItem("proto/generated/persistence.query.proto"));
        resources.produce(new NativeImageResourceBuildItem("proto/generated/persistence.jdbc.proto"));
        resources.produce(new NativeImageResourceBuildItem(WrappedMessage.PROTO_FILE));

        for (Class<?> serviceLoadedInterface : Arrays.asList(ModuleMetadataBuilder.class, ConfigurationParser.class)) {
            // Need to register all the modules as service providers so they can be picked up at runtime
            ServiceLoader<?> serviceLoader = ServiceLoader.load(serviceLoadedInterface);
            List<String> interfaceImplementations = new ArrayList<>();
            serviceLoader.forEach(mmb -> interfaceImplementations.add(mmb.getClass().getName()));
            if (!interfaceImplementations.isEmpty()) {
                serviceProvider
                        .produce(new ServiceProviderBuildItem(serviceLoadedInterface.getName(), interfaceImplementations));
            }
        }

        // Protostream
        // Use CombinedIndex to include both application classes and dependencies
        IndexView combinedIndex = combinedIndexBuildItem.getIndex();
        Collection<ClassInfo> initializerClasses = combinedIndex.getAllKnownImplementors(DotName.createSimple(
                SerializationContextInitializer.class.getName()));
        initializerClasses
                .addAll(combinedIndex.getAllKnownImplementors(DotName.createSimple(GeneratedSchema.class.getName())));

        // Collect both class names (for META-INF/services) and instances (for direct initialization)
        Set<String> initializerNames = new HashSet<>(initializerClasses.size());
        List<SerializationContextInitializer> initializers = new ArrayList<>(initializerClasses.size());

        for (ClassInfo ci : initializerClasses) {
            if (ci.isAbstract()) {
                // don't try to instantiate an abstract class...
                continue;
            }
            try {
                Class<?> initializerClass = Thread.currentThread().getContextClassLoader().loadClass(ci.toString());
                // Try to instantiate to verify it's accessible
                SerializationContextInitializer sci = (SerializationContextInitializer) initializerClass
                        .getDeclaredConstructor().newInstance();
                // Only add if instantiation succeeded
                initializerNames.add(initializerClass.getName());
                initializers.add(sci);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException
                    | ClassNotFoundException | NoSuchMethodException e) {
                // Skip classes that can't be instantiated (e.g., inner classes, package-private classes from other modules)
                // These will be handled by their respective modules or at runtime
                continue;
            }
        }

        // Generate META-INF/services file for ServiceLoader compatibility
        if (!initializerNames.isEmpty()) {
            serviceProvider.produce(
                    new ServiceProviderBuildItem(SerializationContextInitializer.class.getName(), initializerNames));
        }

        Set<DotName> excludedClasses = new HashSet<>();
        excludedReflectionClasses.forEach(excludedBuildItem -> {
            excludedClasses.add(excludedBuildItem.getExcludedClass());
        });

        // This contains parts from the index from the app itself
        Index appOnlyIndex = applicationIndexBuildItem.getIndex();

        // We need to use the CombinedIndex for these interfaces in order to discover implementations of the various
        // subclasses.
        addReflectionForClass(CacheLoader.class, combinedIndex, reflectiveClass, excludedClasses);
        addReflectionForClass(CacheWriter.class, combinedIndex, reflectiveClass, excludedClasses);
        addReflectionForClass(NonBlockingStore.class, combinedIndex, reflectiveClass, excludedClasses);
        addReflectionForName(AsyncInterceptor.class.getName(), true, combinedIndex, reflectiveClass, false, true,
                excludedClasses);

        // Add user listeners
        Collection<AnnotationInstance> listenerInstances = combinedIndex.getAnnotations(
                DotName.createSimple(Listener.class.getName()));

        for (AnnotationInstance instance : listenerInstances) {
            AnnotationTarget target = instance.target();
            if (target.kind() == AnnotationTarget.Kind.CLASS) {
                DotName targetName = target.asClass().name();
                if (!excludedClasses.contains(targetName)) {
                    reflectiveClass.produce(
                            ReflectiveClassBuildItem.builder(target.toString()).methods().build());
                }
            }
        }

        // We only register the app advanced externalizers as all of the Infinispan ones are explicitly defined
        addReflectionForClass(AdvancedExternalizer.class, appOnlyIndex, reflectiveClass, Collections.emptySet());
        // Due to the index not containing AbstractExternalizer it doesn't know that it implements AdvancedExternalizer
        // thus we also have to include classes that extend AbstractExternalizer
        addReflectionForClass(AbstractExternalizer.class, appOnlyIndex, reflectiveClass, Collections.emptySet());
        addReflectionForClass(CacheHealth.class, appOnlyIndex, reflectiveClass, Collections.emptySet());
        addReflectionForClass(ClusterHealth.class, appOnlyIndex, reflectiveClass, Collections.emptySet());

        // Add optional SQL classes. These will only be included if the optional jars are present on the classpath and indexed by Jandex.
        addReflectionForName("org.infinispan.persistence.jdbc.common.configuration.ConnectionFactoryConfiguration", true,
                combinedIndex, reflectiveClass, true, false, excludedClasses);
        addReflectionForName("org.infinispan.persistence.jdbc.common.configuration.ConnectionFactoryConfigurationBuilder", true,
                combinedIndex, reflectiveClass, true, false, excludedClasses);
        addReflectionForName("org.infinispan.persistence.jdbc.common.configuration.AbstractSchemaJdbcConfigurationBuilder",
                false, combinedIndex, reflectiveClass, true, false, excludedClasses);
        addReflectionForName("org.infinispan.persistence.jdbc.common.connectionfactory.ConnectionFactory", false, combinedIndex,
                reflectiveClass, false, false, excludedClasses);
        addReflectionForName("org.infinispan.persistence.keymappers.Key2StringMapper", true, combinedIndex, reflectiveClass,
                false, false, excludedClasses);

        // Ensure that optional store implementations not included in core-graalvm are still detected
        addReflectionForClass(StoreConfigurationBuilder.class, combinedIndex, reflectiveClass, excludedClasses);
        addReflectionForClass(StoreConfiguration.class, combinedIndex, reflectiveClass, true, excludedClasses);
        addReflectionForClass(ConfigurationSerializer.class, combinedIndex, reflectiveClass, excludedClasses);
        addReflectionForClass(AbstractModuleConfigurationBuilder.class, combinedIndex, reflectiveClass, excludedClasses);

        return new ProtobufInitializers(initializers);
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    BeanContainerListenerBuildItem build(InfinispanRecorder recorder, ProtobufInitializers initializers) {
        // This is necessary to be done for Protostream Marshaller init in native
        return new BeanContainerListenerBuildItem(recorder.configureInfinispan(initializers.getInitializers()));
    }

    private void addReflectionForClass(Class<?> classToUse, IndexView indexView,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClass, boolean methods, Set<DotName> excludedClasses) {
        addReflectionForName(classToUse.getName(), classToUse.isInterface(), indexView, reflectiveClass, methods, false,
                excludedClasses);
    }

    private void addReflectionForClass(Class<?> classToUse, IndexView indexView,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClass, Set<DotName> excludedClasses) {
        addReflectionForClass(classToUse, indexView, reflectiveClass, false, excludedClasses);
    }

    private void addReflectionForName(String className, boolean isInterface, IndexView indexView,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClass, boolean methods, boolean fields,
            Set<DotName> excludedClasses) {
        Collection<ClassInfo> classInfos;
        if (isInterface) {
            classInfos = indexView.getAllKnownImplementors(DotName.createSimple(className));
        } else {
            classInfos = indexView.getAllKnownSubclasses(DotName.createSimple(className));
        }

        classInfos.removeIf(ci -> excludedClasses.contains(ci.name()));

        if (!classInfos.isEmpty()) {
            String[] classNames = classInfos.stream().map(ClassInfo::toString).toArray(String[]::new);
            reflectiveClass.produce(
                    ReflectiveClassBuildItem.builder(classNames)
                            .methods(methods)
                            .fields(fields)
                            .build());
        }
    }

    @BuildStep
    UnremovableBeanBuildItem ensureBeanLookupAvailable() {
        return UnremovableBeanBuildItem.beanTypes(BaseMarshaller.class, EnumMarshaller.class, MessageMarshaller.class,
                FileDescriptorSource.class, Schema.class, SerializationContextInitializer.class, EmbeddedCacheManager.class);
    }

    @BuildStep
    void nativeImage(BuildProducer<ReflectiveClassBuildItem> producer) {
        producer.produce(ReflectiveClassBuildItem.builder(CompositeCacheKey.class)
                .reason(getClass().getName())
                .methods(true).build());
    }

    @Record(RUNTIME_INIT)
    @BuildStep
    void generateClientBeans(InfinispanRecorder recorder,
            BeanRegistrationPhaseBuildItem registrationPhase,
            BeanDiscoveryFinishedBuildItem finishedBuildItem,
            BeanDiscoveryFinishedBuildItem beans,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeanBuildItemBuildProducer) {

        syntheticBeanBuildItemBuildProducer.produce(
                configureAndCreateSyntheticBean(EmbeddedCacheManager.class,
                        recorder.infinispanEmbeddedSupplier()));

        beans.getInjectionPoints().stream()
                .filter(ip -> ip.getRequiredQualifier(INFINISPAN_EMBEDDED_ANNOTATION) != null)
                .map(ip -> {
                    AnnotationInstance cacheQualifier = ip.getRequiredQualifier(INFINISPAN_EMBEDDED_ANNOTATION);
                    CacheBean cacheBean = new CacheBean(ip.getType(), cacheQualifier.value().asString());
                    return cacheBean;
                }).forEach(cacheBean ->
                // Produce Cache and AdvancedCache beans
                syntheticBeanBuildItemBuildProducer.produce(
                        configureAndCreateSyntheticBean(cacheBean, AdvancedCache.class,
                                recorder.infinispanAdvancedCacheSupplier(cacheBean.cacheName))));
    }

    static <T> SyntheticBeanBuildItem configureAndCreateSyntheticBean(Class<T> type,
            Supplier<T> supplier) {

        SyntheticBeanBuildItem.ExtendedBeanConfigurator configurator = SyntheticBeanBuildItem
                .configure(type)
                .supplier(supplier)
                .scope(ApplicationScoped.class)
                .addQualifier(Default.class)
                .unremovable()
                .setRuntimeInit();
        return configurator.done();
    }

    static <T> SyntheticBeanBuildItem configureAndCreateSyntheticBean(CacheBean cacheBean, Class<?> implClazz,
            Supplier<T> supplier) {
        SyntheticBeanBuildItem.ExtendedBeanConfigurator configurator = SyntheticBeanBuildItem.configure(implClazz)
                .types(cacheBean.type)
                .scope(ApplicationScoped.class)
                .supplier(supplier)
                .unremovable()
                .setRuntimeInit();
        configurator.addQualifier().annotation(INFINISPAN_EMBEDDED_ANNOTATION).addValue("value", cacheBean.cacheName)
                .done();
        return configurator.done();
    }

    record CacheBean(Type type, String cacheName) {
    }

    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep
    void configureRuntimeProperties(InfinispanRecorder recorder, InfinispanEmbeddedRuntimeConfig runtimeConfig) {
        recorder.configureRuntimeProperties(runtimeConfig);
    }

    @BuildStep
    @Record(RUNTIME_INIT)
    CacheManagerInfoBuildItem cacheManagerInfo(BuildProducer<SyntheticBeanBuildItem> syntheticBeanBuildItemBuildProducer,
            InfinispanRecorder recorder) {
        return new CacheManagerInfoBuildItem(recorder.getCacheManagerSupplier());
    }
}

package io.quarkiverse.infinispan.embedded.deployment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

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
import org.infinispan.notifications.Listener;
import org.infinispan.persistence.spi.CacheWriter;
import org.infinispan.persistence.spi.NonBlockingStore;
import org.infinispan.protostream.BaseMarshaller;
import org.infinispan.protostream.EnumMarshaller;
import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.GeneratedSchema;
import org.infinispan.protostream.MessageMarshaller;
import org.infinispan.protostream.SerializationContextInitializer;
import org.infinispan.protostream.schema.Schema;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexView;

import com.github.benmanes.caffeine.cache.CacheLoader;

import io.quarkiverse.infinispan.embedded.runtime.InfinispanEmbeddedProducer;
import io.quarkiverse.infinispan.embedded.runtime.InfinispanEmbeddedRuntimeConfig;
import io.quarkiverse.infinispan.embedded.runtime.InfinispanRecorder;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
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
    }

    @BuildStep
    void setup(BuildProducer<ReflectiveClassBuildItem> reflectiveClass,
            BuildProducer<ServiceProviderBuildItem> serviceProvider, BuildProducer<AdditionalBeanBuildItem> additionalBeans,
            BuildProducer<NativeImageResourceBuildItem> resources, CombinedIndexBuildItem combinedIndexBuildItem,
            List<InfinispanReflectionExcludedBuildItem> excludedReflectionClasses,
            ApplicationIndexBuildItem applicationIndexBuildItem) {

        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(InfinispanEmbeddedProducer.class));

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
        Index index = applicationIndexBuildItem.getIndex();
        Collection<ClassInfo> initializerClasses = index.getAllKnownImplementors(DotName.createSimple(
                SerializationContextInitializer.class.getName()));
        initializerClasses
                .addAll(index.getAllKnownImplementors(DotName.createSimple(GeneratedSchema.class.getName())));
        Set<String> initializers = new HashSet<>(initializerClasses.size());
        for (ClassInfo ci : initializerClasses) {
            Class<?> initializerClass = null;
            try {
                initializerClass = Thread.currentThread().getContextClassLoader().loadClass(ci.toString());
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
            initializers.add(initializerClass.getName());
        }
        serviceProvider
                .produce(new ServiceProviderBuildItem(SerializationContextInitializer.class.getName(), initializers));

        Set<DotName> excludedClasses = new HashSet<>();
        excludedReflectionClasses.forEach(excludedBuildItem -> {
            excludedClasses.add(excludedBuildItem.getExcludedClass());
        });

        // This contains parts from the index from the app itself
        Index appOnlyIndex = applicationIndexBuildItem.getIndex();

        // Persistence SPIs
        IndexView combinedIndex = combinedIndexBuildItem.getIndex();

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

        resources.produce(new NativeImageResourceBuildItem(
                "proto/generated/persistence.jdbc.proto"));

        // Ensure that optional store implementations not included in core-graalvm are still detected
        addReflectionForClass(StoreConfigurationBuilder.class, combinedIndex, reflectiveClass, excludedClasses);
        addReflectionForClass(StoreConfiguration.class, combinedIndex, reflectiveClass, true, excludedClasses);
        addReflectionForClass(ConfigurationSerializer.class, combinedIndex, reflectiveClass, excludedClasses);
        addReflectionForClass(AbstractModuleConfigurationBuilder.class, combinedIndex, reflectiveClass, excludedClasses);
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
                FileDescriptorSource.class, Schema.class, SerializationContextInitializer.class);
    }

    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep
    void configureRuntimeProperties(InfinispanRecorder recorder, InfinispanEmbeddedRuntimeConfig runtimeConfig) {
        recorder.configureRuntimeProperties(runtimeConfig);
    }
}

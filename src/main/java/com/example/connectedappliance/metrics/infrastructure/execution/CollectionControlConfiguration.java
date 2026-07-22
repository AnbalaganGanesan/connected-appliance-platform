package com.example.connectedappliance.metrics.infrastructure.execution;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.example.connectedappliance.metrics.application.control.ApplianceCollectionGuard;
import com.example.connectedappliance.metrics.application.control.CollectionBackoffPolicy;
import com.example.connectedappliance.metrics.application.control.CollectionSchedulingPolicy;
import com.example.connectedappliance.metrics.application.control.GuardedVendorExecution;
import com.example.connectedappliance.metrics.application.control.InMemoryApplianceCollectionGuard;
import com.example.connectedappliance.metrics.application.control.VendorFailureClassifier;
import com.example.connectedappliance.metrics.application.control.VendorTaskExecutor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Spring composition for the bounded vendor executor and framework-free control policies. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CollectionControlProperties.class)
public class CollectionControlConfiguration {

    public static final String VENDOR_COLLECTION_EXECUTOR = "vendorCollectionExecutor";
    public static final String THREAD_NAME_PREFIX = "vendor-collection-";

    @Bean
    ApplianceCollectionGuard applianceCollectionGuard() {
        return new InMemoryApplianceCollectionGuard();
    }

    @Bean(name = VENDOR_COLLECTION_EXECUTOR, destroyMethod = "shutdown")
    ThreadPoolExecutor vendorCollectionExecutor(CollectionControlProperties properties) {
        int executorSize = properties.executorSize();
        return new ThreadPoolExecutor(
                executorSize,
                executorSize,
                0,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(properties.executorQueueCapacity()),
                vendorThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean
    VendorTaskExecutor vendorTaskExecutor(
            @Qualifier(VENDOR_COLLECTION_EXECUTOR) ThreadPoolExecutor executor) {
        return new ThreadPoolVendorTaskExecutor(executor);
    }

    @Bean
    VendorFailureClassifier vendorFailureClassifier() {
        return new VendorFailureClassifier();
    }

    @Bean
    GuardedVendorExecution guardedVendorExecution(
            ApplianceCollectionGuard guard,
            VendorTaskExecutor executor,
            VendorFailureClassifier failureClassifier,
            CollectionControlProperties properties) {
        return new GuardedVendorExecution(
                guard, executor, failureClassifier, properties.vendorTimeout());
    }

    @Bean
    CollectionBackoffPolicy collectionBackoffPolicy(CollectionControlProperties properties) {
        return new CollectionBackoffPolicy(properties.backoffCap());
    }

    @Bean
    CollectionSchedulingPolicy collectionSchedulingPolicy(
            CollectionBackoffPolicy backoffPolicy) {
        return new CollectionSchedulingPolicy(backoffPolicy);
    }

    private ThreadFactory vendorThreadFactory() {
        AtomicInteger sequence = new AtomicInteger();
        return task -> new Thread(task, THREAD_NAME_PREFIX + sequence.incrementAndGet());
    }
}

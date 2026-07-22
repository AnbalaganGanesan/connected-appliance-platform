package com.example.connectedappliance.metrics.infrastructure.execution;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.sql.DataSource;

import com.example.connectedappliance.metrics.application.control.ApplianceCollectionGuard;
import com.example.connectedappliance.metrics.application.control.CollectionBackoffPolicy;
import com.example.connectedappliance.metrics.application.control.CollectionSchedulingPolicy;
import com.example.connectedappliance.metrics.application.control.GuardedVendorExecution;
import com.example.connectedappliance.vendor.application.VendorScenario;
import com.example.connectedappliance.vendor.infrastructure.mockalpha.MockAlphaProperties;
import com.example.connectedappliance.vendor.infrastructure.mockbeta.MockBetaProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class CollectionControlConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(
                    CollectionControlConfiguration.class, MockPropertiesConfiguration.class);

    @Test
    void bindsApprovedDefaultsAndCreatesControlBeansWithoutDatabase() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            CollectionControlProperties properties =
                    context.getBean(CollectionControlProperties.class);
            assertThat(properties.vendorTimeout()).isEqualTo(Duration.ofSeconds(2));
            assertThat(properties.executorSize()).isEqualTo(4);
            assertThat(properties.executorQueueCapacity()).isEqualTo(16);
            assertThat(properties.backoffCap()).isEqualTo(Duration.ofHours(24));
            assertThat(context).hasSingleBean(ApplianceCollectionGuard.class);
            assertThat(context).hasSingleBean(GuardedVendorExecution.class);
            assertThat(context).hasSingleBean(CollectionBackoffPolicy.class);
            assertThat(context).hasSingleBean(CollectionSchedulingPolicy.class);
            assertThat(context).doesNotHaveBean(DataSource.class);
        });
    }

    @Test
    void productionExecutorIsFixedBoundedAbortPolicyNamedAndLifecycleManaged() {
        AtomicReference<ThreadPoolExecutor> capturedExecutor = new AtomicReference<>();

        contextRunner.run(context -> {
            ThreadPoolExecutor executor = context.getBean(
                    CollectionControlConfiguration.VENDOR_COLLECTION_EXECUTOR,
                    ThreadPoolExecutor.class);
            capturedExecutor.set(executor);

            assertThat(executor.getCorePoolSize()).isEqualTo(4);
            assertThat(executor.getMaximumPoolSize()).isEqualTo(4);
            assertThat(executor.getQueue()).isInstanceOf(ArrayBlockingQueue.class);
            assertThat(executor.getQueue().remainingCapacity()).isEqualTo(16);
            assertThat(executor.getRejectedExecutionHandler())
                    .isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);
            assertThat(executorThreadName(executor))
                    .startsWith(CollectionControlConfiguration.THREAD_NAME_PREFIX);
        });

        assertThat(capturedExecutor.get()).isNotNull();
        assertThat(capturedExecutor.get().isShutdown()).isTrue();
    }

    @Test
    void everyCollectionPropertyCanBeOverriddenIndependently() {
        assertSingleOverride(
                "app.collection.vendor-timeout=PT3S",
                properties -> assertThat(properties.vendorTimeout()).isEqualTo(Duration.ofSeconds(3)));
        assertSingleOverride(
                "app.collection.executor-size=2",
                properties -> assertThat(properties.executorSize()).isEqualTo(2));
        assertSingleOverride(
                "app.collection.executor-queue-capacity=7",
                properties -> assertThat(properties.executorQueueCapacity()).isEqualTo(7));
        assertSingleOverride(
                "app.collection.backoff-cap=PT1H",
                properties -> assertThat(properties.backoffCap()).isEqualTo(Duration.ofHours(1)));
    }

    @Test
    void rejectsZeroNegativeAndFractionalTimeouts() {
        assertInvalid("app.collection.vendor-timeout=PT0S");
        assertInvalid("app.collection.vendor-timeout=-PT1S");
        assertInvalid("app.collection.vendor-timeout=PT0.5S");
    }

    @Test
    void rejectsZeroAndNegativeExecutorSizesAndQueueCapacities() {
        assertInvalid("app.collection.executor-size=0");
        assertInvalid("app.collection.executor-size=-1");
        assertInvalid("app.collection.executor-queue-capacity=0");
        assertInvalid("app.collection.executor-queue-capacity=-1");
    }

    @Test
    void rejectsZeroNegativeAndFractionalBackoffCaps() {
        assertInvalid("app.collection.backoff-cap=PT0S");
        assertInvalid("app.collection.backoff-cap=-PT1S");
        assertInvalid("app.collection.backoff-cap=PT1.5S");
    }

    @Test
    void taskFourteenMockVendorDefaultsStillBind() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(MockAlphaProperties.class).scenario())
                    .isEqualTo(VendorScenario.SUCCESS);
            assertThat(context.getBean(MockAlphaProperties.class).delay())
                    .isEqualTo(Duration.ZERO);
            assertThat(context.getBean(MockBetaProperties.class).scenario())
                    .isEqualTo(VendorScenario.SUCCESS);
            assertThat(context.getBean(MockBetaProperties.class).delay())
                    .isEqualTo(Duration.ZERO);
        });
    }

    private void assertSingleOverride(
            String propertyValue,
            java.util.function.Consumer<CollectionControlProperties> assertion) {
        contextRunner.withPropertyValues(propertyValue).run(context -> {
            assertThat(context).hasNotFailed();
            CollectionControlProperties properties =
                    context.getBean(CollectionControlProperties.class);
            assertion.accept(properties);
        });
    }

    private void assertInvalid(String propertyValue) {
        contextRunner.withPropertyValues(propertyValue)
                .run(context -> assertThat(context).hasFailed());
    }

    private String executorThreadName(ThreadPoolExecutor executor) {
        try {
            return executor.submit(() -> Thread.currentThread().getName())
                    .get(1, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new IllegalStateException("executor test task did not complete", failure);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({MockAlphaProperties.class, MockBetaProperties.class})
    static class MockPropertiesConfiguration {}
}

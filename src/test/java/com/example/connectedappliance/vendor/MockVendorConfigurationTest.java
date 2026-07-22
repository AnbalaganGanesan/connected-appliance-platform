package com.example.connectedappliance.vendor;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import com.example.connectedappliance.vendor.application.VendorScenario;
import com.example.connectedappliance.vendor.infrastructure.mockalpha.MockAlphaProperties;
import com.example.connectedappliance.vendor.infrastructure.mockbeta.MockBetaProperties;

import static org.assertj.core.api.Assertions.assertThat;

class MockVendorConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void loadsSuccessAndZeroDelayDefaultsFromApplicationConfiguration() {
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

    @Test
    void bindsAlphaOverridesWithoutChangingBeta() {
        contextRunner
                .withPropertyValues(
                        "app.mock-vendors.mock-alpha.scenario=PARTIAL",
                        "app.mock-vendors.mock-alpha.delay=PT0.25S")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(MockAlphaProperties.class).scenario())
                            .isEqualTo(VendorScenario.PARTIAL);
                    assertThat(context.getBean(MockAlphaProperties.class).delay())
                            .isEqualTo(Duration.ofMillis(250));
                    assertThat(context.getBean(MockBetaProperties.class).scenario())
                            .isEqualTo(VendorScenario.SUCCESS);
                    assertThat(context.getBean(MockBetaProperties.class).delay())
                            .isEqualTo(Duration.ZERO);
                });
    }

    @Test
    void bindsBetaOverridesWithoutChangingAlpha() {
        contextRunner
                .withPropertyValues(
                        "app.mock-vendors.mock-beta.scenario=RATE_LIMITED",
                        "app.mock-vendors.mock-beta.delay=PT0.5S")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(MockBetaProperties.class).scenario())
                            .isEqualTo(VendorScenario.RATE_LIMITED);
                    assertThat(context.getBean(MockBetaProperties.class).delay())
                            .isEqualTo(Duration.ofMillis(500));
                    assertThat(context.getBean(MockAlphaProperties.class).scenario())
                            .isEqualTo(VendorScenario.SUCCESS);
                    assertThat(context.getBean(MockAlphaProperties.class).delay())
                            .isEqualTo(Duration.ZERO);
                });
    }

    @Test
    void rejectsNegativeDelayThroughConfigurationValidation() {
        contextRunner
                .withPropertyValues("app.mock-vendors.mock-alpha.delay=-PT0.001S")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(hasCause(context.getStartupFailure(), BindValidationException.class))
                            .isTrue();
                });
    }

    @Test
    void rejectsUnknownScenarioDuringBinding() {
        contextRunner
                .withPropertyValues("app.mock-vendors.mock-beta.scenario=NOT_APPROVED")
                .run(context -> assertThat(context).hasFailed());
    }

    private static boolean hasCause(Throwable failure, Class<? extends Throwable> causeType) {
        Throwable current = failure;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({MockAlphaProperties.class, MockBetaProperties.class})
    static class PropertiesConfiguration {}
}

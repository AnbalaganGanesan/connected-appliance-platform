package com.example.connectedappliance.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.example.connectedappliance.ConnectedAppliancePlatformApplication;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationPropertiesConventionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ConnectedAppliancePlatformApplication.class);

    @Test
    void bindsValidConfigurationProperties() {
        contextRunner
                .withPropertyValues(
                        "test.fixture.name=review-fixture",
                        "test.fixture.maximum-items=7")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(TestFixtureProperties.class);

                    TestFixtureProperties properties = context.getBean(TestFixtureProperties.class);
                    assertThat(properties.getName()).isEqualTo("review-fixture");
                    assertThat(properties.getMaximumItems()).isEqualTo(7);
                });
    }

    @Test
    void rejectsConfigurationPropertiesThatFailJakartaValidation() {
        contextRunner
                .withPropertyValues(
                        "test.fixture.name=review-fixture",
                        "test.fixture.maximum-items=0")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(hasCause(context.getStartupFailure(), BindValidationException.class))
                            .isTrue();
                });
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
}

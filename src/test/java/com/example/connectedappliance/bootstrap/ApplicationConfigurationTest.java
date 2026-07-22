package com.example.connectedappliance.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationConfigurationTest {

    private static final String DEFAULT_JDBC_URL =
            "jdbc:postgresql://localhost:5432/connected_appliance";

    private final ApplicationContextRunner localConfigurationContext =
            new ApplicationContextRunner()
                    .withInitializer(new ConfigDataApplicationContextInitializer())
                    .withPropertyValues("spring.profiles.active=local");

    @Test
    void loadsApprovedCommonAndLocalConfiguration() {
        localConfigurationContext.run(context -> {
            assertThat(context).hasNotFailed();

            Environment environment = context.getEnvironment();
            assertThat(environment.getProperty("spring.application.name"))
                    .isEqualTo("connected-appliance-platform");
            assertThat(environment.getProperty(
                    "spring.jackson.deserialization.fail-on-unknown-properties",
                    Boolean.class)).isTrue();
            assertThat(environment.getProperty("spring.datasource.url"))
                    .isEqualTo(DEFAULT_JDBC_URL);
            assertThat(environment.getProperty("spring.datasource.username"))
                    .isEqualTo("connected_appliance");
            assertThat(environment.containsProperty("spring.datasource.password")).isTrue();
            assertThat(environment.getProperty("spring.jpa.open-in-view", Boolean.class))
                    .isFalse();
            assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto"))
                    .isEqualTo("validate");
            assertThat(environment.getProperty("management.endpoints.web.exposure.include"))
                    .isEqualTo("health");
            assertThat(environment.getProperty("management.endpoint.health.show-details"))
                    .isEqualTo("never");
            assertThat(environment.getProperty("management.endpoint.health.show-components"))
                    .isEqualTo("never");
        });
    }

    @Test
    void supportsEnvironmentStyleDatasourceOverrides() {
        localConfigurationContext
                .withPropertyValues(
                        "APP_DB_URL=jdbc:postgresql://database.example:5432/review",
                        "APP_DB_USERNAME=review_user",
                        "APP_DB_PASSWORD=review_password")
                .run(context -> {
                    Environment environment = context.getEnvironment();
                    assertThat(environment.getProperty("spring.datasource.url"))
                            .isEqualTo("jdbc:postgresql://database.example:5432/review");
                    assertThat(environment.getProperty("spring.datasource.username"))
                            .isEqualTo("review_user");
                    assertThat("review_password".equals(
                            environment.getProperty("spring.datasource.password"))).isTrue();
                });
    }
}

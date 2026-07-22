package com.example.connectedappliance.integration;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Supplies one PostgreSQL container to integration-test classes in the current Failsafe JVM.
 *
 * <p>The singleton is scoped to that forked integration-test JVM. Testcontainers reusable-container
 * mode is intentionally disabled, so every new Maven verify process creates a fresh container.
 * Testcontainers and its resource reaper clean up the container when the forked JVM exits.
 */
public abstract class PostgresIntegrationTestSupport {

    private static final DockerImageName POSTGRES_IMAGE =
            DockerImageName.parse("postgres:17.10-alpine3.24");

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(POSTGRES_IMAGE)
                    .withDatabaseName("connected_appliance_test")
                    .withUsername("integration_test_user")
                    .withPassword("integration_test_password");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void configurePostgresDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    protected static String postgresJdbcUrl() {
        return POSTGRES.getJdbcUrl();
    }

    protected static String postgresDatabaseName() {
        return POSTGRES.getDatabaseName();
    }

    protected static String postgresContainerId() {
        return POSTGRES.getContainerId();
    }

    protected static int postgresMappedPort() {
        return POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT);
    }

    protected static boolean postgresIsRunning() {
        return POSTGRES.isRunning();
    }
}

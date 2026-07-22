package com.example.connectedappliance.integration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Locale;

import javax.sql.DataSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Table;
import jakarta.persistence.metamodel.EntityType;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthContributorRegistry;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DatabaseSmokeIT extends PostgresIntegrationTestSupport {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private HealthContributorRegistry healthContributorRegistry;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Environment environment;

    @Test
    void connectsToTheTestcontainersPostgresDatabase() throws Exception {
        assertThat(postgresIsRunning()).isTrue();
        assertThat(postgresContainerId()).isNotBlank();
        assertThat(postgresMappedPort()).isPositive();

        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL()).isEqualTo(postgresJdbcUrl());
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");

            try (PreparedStatement statement = connection.prepareStatement("SELECT 1");
                    ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(1);
            }
        }

        assertThat(jdbcTemplate.queryForObject("SELECT current_database()", String.class))
                .isEqualTo(postgresDatabaseName());
    }

    @Test
    void initializesFlywayAndJpaWithOnlyTheApprovedApplianceMapping() {
        assertThatCode(flyway::validate).doesNotThrowAnyException();
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.info().applied()).hasSize(1);
        assertThat(flyway.info().applied()[0].getVersion().toString()).isEqualTo("1");
        assertThat(flyway.info().applied()[0].getDescription()).isEqualTo("create appliance");
        assertThat(flyway.info().all())
                .noneMatch(info -> info.getState().name().contains("FAILED"));
        assertThat(entityManagerFactory.isOpen()).isTrue();
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto"))
                .isEqualTo("validate");

        assertThat(entityManagerFactory.getMetamodel().getEntities()).hasSize(1);
        EntityType<?> applianceEntity =
                entityManagerFactory.getMetamodel().getEntities().iterator().next();
        assertThat(applianceEntity.getName()).isEqualTo("ApplianceEntity");
        assertThat(applianceEntity.getJavaType().getPackageName())
                .isEqualTo(
                        "com.example.connectedappliance.appliance.infrastructure.persistence");
        assertThat(applianceEntity.getJavaType().getAnnotation(Table.class))
                .isNotNull()
                .extracting(Table::name)
                .isEqualTo("appliance");
        assertThat(applianceEntity.getJavaType().getPackageName())
                .doesNotContain("metrics", "reporting", "vendor");

        assertThat(jdbcTemplate.queryForList(
                """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name <> 'flyway_schema_history'
                ORDER BY table_name
                """,
                String.class))
                .containsExactly("appliance");
    }

    @Test
    void reportsSanitizedUpHealthWithTheDatabaseContributor() throws Exception {
        assertThat(healthContributorRegistry.getContributor("db")).isNotNull();

        ResponseEntity<String> response =
                restTemplate.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("status").asText()).isEqualTo("UP");
        assertThat(body.has("components")).isFalse();

        String responseBody = response.getBody().toLowerCase(Locale.ROOT);
        assertThat(responseBody)
                .doesNotContain(
                        "jdbc:",
                        postgresDatabaseName().toLowerCase(Locale.ROOT),
                        "username",
                        "password",
                        "driver",
                        "sql",
                        "exception",
                        "components");
    }
}

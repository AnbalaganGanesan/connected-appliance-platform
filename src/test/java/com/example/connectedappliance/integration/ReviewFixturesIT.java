package com.example.connectedappliance.integration;

import java.math.BigDecimal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.example.connectedappliance.bootstrap.ReviewFixturesRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test that verifies the optional reviewer fixtures work correctly against a real
 * PostgreSQL database provided by Testcontainers.
 *
 * <p>The {@code review-fixtures} profile activates {@link ReviewFixturesRunner} in the Spring
 * context. The runner executes automatically at context startup. Each test method cleans the
 * database via {@code @BeforeEach} and then manually invokes the runner to control the
 * run under test, making assertions independent of startup timing.
 *
 * <p>Run with: {@code ./mvnw -q -Dit.test=ReviewFixturesIT verify}
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("review-fixtures")
@Execution(ExecutionMode.SAME_THREAD)
class ReviewFixturesIT extends PostgresIntegrationTestSupport {

    @Autowired
    private ReviewFixturesRunner runner;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM metric_sample");
        jdbcTemplate.update("DELETE FROM collection_warning");
        jdbcTemplate.update("DELETE FROM collection_attempt");
        jdbcTemplate.update("DELETE FROM appliance");
    }

    // ── First run — exact counts ──────────────────────────────────────────────────

    @Test
    void firstRun_produces20Appliances() throws Exception {
        runner.run(null);
        assertThat(count("appliance")).isEqualTo(20);
    }

    @Test
    void firstRun_produces10AlphaAnd10BetaAppliances() throws Exception {
        runner.run(null);
        assertThat(count("appliance", "vendor_key = 'mock-alpha'")).isEqualTo(10);
        assertThat(count("appliance", "vendor_key = 'mock-beta'")).isEqualTo(10);
    }

    @Test
    void firstRun_produces18ActiveAnd2PausedAppliances() throws Exception {
        runner.run(null);
        assertThat(count("appliance", "collection_state = 'ACTIVE'")).isEqualTo(18);
        assertThat(count("appliance", "collection_state = 'PAUSED'")).isEqualTo(2);
    }

    @Test
    void firstRun_produces40CollectionAttempts() throws Exception {
        runner.run(null);
        assertThat(count("collection_attempt")).isEqualTo(40);
    }

    @Test
    void firstRun_allAttemptsAreManualAndSuccessful() throws Exception {
        runner.run(null);
        assertThat(count("collection_attempt", "trigger = 'MANUAL'")).isEqualTo(40);
        assertThat(count("collection_attempt", "outcome = 'SUCCESS'")).isEqualTo(40);
    }

    @Test
    void firstRun_produces80MetricSamples() throws Exception {
        runner.run(null);
        assertThat(count("metric_sample")).isEqualTo(80);
    }

    @Test
    void firstRun_twoSamplesPerSuccessfulAttempt() throws Exception {
        runner.run(null);
        // Every attempt has sample_count=2 (TEMPERATURE + POWER)
        int attemptsWithTwoSamples = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM collection_attempt WHERE sample_count = 2",
                Integer.class);
        assertThat(attemptsWithTwoSamples).isEqualTo(40);
    }

    @Test
    void firstRun_producesZeroWarnings() throws Exception {
        runner.run(null);
        assertThat(count("collection_warning")).isEqualTo(0);
    }

    // ── Vendor normalization verification ─────────────────────────────────────────

    @Test
    void firstRun_alphaSamplesAreCanonicalTemperatureAndPower() throws Exception {
        runner.run(null);
        // All Alpha samples must be TEMPERATURE/CELSIUS or POWER/WATT
        int alphaTemperature = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM metric_sample ms "
                        + "JOIN appliance a ON ms.appliance_id = a.id "
                        + "WHERE a.vendor_key = 'mock-alpha' "
                        + "AND ms.metric_name = 'TEMPERATURE' AND ms.unit = 'CELSIUS'",
                Integer.class);
        assertThat(alphaTemperature).isGreaterThan(0);

        int alphaPower = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM metric_sample ms "
                        + "JOIN appliance a ON ms.appliance_id = a.id "
                        + "WHERE a.vendor_key = 'mock-alpha' "
                        + "AND ms.metric_name = 'POWER' AND ms.unit = 'WATT'",
                Integer.class);
        assertThat(alphaPower).isGreaterThan(0);
    }

    @Test
    void firstRun_alphaTemperatureValueIs21Point5Celsius() throws Exception {
        runner.run(null);
        int matching = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM metric_sample ms "
                        + "JOIN appliance a ON ms.appliance_id = a.id "
                        + "WHERE a.vendor_key = 'mock-alpha' "
                        + "AND ms.metric_name = 'TEMPERATURE' "
                        + "AND ms.value = 21.500000",
                Integer.class);
        // 20 Alpha attempts × 1 temperature sample each
        assertThat(matching).isEqualTo(20);
    }

    @Test
    void firstRun_betaFahrenheitConvertedToCelsius22() throws Exception {
        runner.run(null);
        // Beta sends 71.6°F which normalises to 22.000000°C
        int matching = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM metric_sample ms "
                        + "JOIN appliance a ON ms.appliance_id = a.id "
                        + "WHERE a.vendor_key = 'mock-beta' "
                        + "AND ms.metric_name = 'TEMPERATURE' "
                        + "AND ms.value = 22.000000",
                Integer.class);
        // 20 Beta attempts × 1 temperature sample each
        assertThat(matching).isEqualTo(20);
    }

    @Test
    void firstRun_betaKilowattsConvertedToWatts150() throws Exception {
        runner.run(null);
        // Beta sends 0.150000 kW which normalises to 150.000000 W
        int matching = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM metric_sample ms "
                        + "JOIN appliance a ON ms.appliance_id = a.id "
                        + "WHERE a.vendor_key = 'mock-beta' "
                        + "AND ms.metric_name = 'POWER' "
                        + "AND ms.value = 150.000000",
                Integer.class);
        // 20 Beta attempts × 1 power sample each
        assertThat(matching).isEqualTo(20);
    }

    // ── State constraints ─────────────────────────────────────────────────────────

    @Test
    void firstRun_pausedAppliancesHaveNullNextCollectionDueAt() throws Exception {
        runner.run(null);
        int pausedWithNullDue = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM appliance "
                        + "WHERE collection_state = 'PAUSED' "
                        + "AND next_collection_due_at IS NULL",
                Integer.class);
        assertThat(pausedWithNullDue).isEqualTo(2);
    }

    @Test
    void firstRun_activeAppliancesHaveNonNullNextCollectionDueAt() throws Exception {
        runner.run(null);
        int activeWithNullDue = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM appliance "
                        + "WHERE collection_state = 'ACTIVE' "
                        + "AND next_collection_due_at IS NULL",
                Integer.class);
        assertThat(activeWithNullDue).isEqualTo(0);
    }

    // ── External reference separation ────────────────────────────────────────────

    @Test
    void fixtureExternalReferences_doNotOverlapWithPostmanDemoReferences() throws Exception {
        runner.run(null);
        // Postman demo references are alpha-demo-001 and beta-demo-001
        int postmanDemoConflict = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM appliance "
                        + "WHERE external_reference IN ('alpha-demo-001', 'beta-demo-001')",
                Integer.class);
        assertThat(postmanDemoConflict).isEqualTo(0);
    }

    @Test
    void fixtureExternalReferences_allUseSeedPrefix() throws Exception {
        runner.run(null);
        int withSeedPrefix = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM appliance "
                        + "WHERE external_reference LIKE 'alpha-seed-%' "
                        + "   OR external_reference LIKE 'beta-seed-%'",
                Integer.class);
        assertThat(withSeedPrefix).isEqualTo(20);
    }

    // ── Second run — idempotency after completed first run ────────────────────────

    @Test
    void secondRun_applianceCountUnchanged() throws Exception {
        runner.run(null);
        int afterFirst = count("appliance");

        runner.run(null); // second run — all are duplicates

        assertThat(count("appliance")).isEqualTo(afterFirst);
    }

    @Test
    void secondRun_attemptCountUnchanged() throws Exception {
        runner.run(null);
        int afterFirst = count("collection_attempt");

        runner.run(null);

        assertThat(count("collection_attempt")).isEqualTo(afterFirst);
    }

    @Test
    void secondRun_sampleCountUnchanged() throws Exception {
        runner.run(null);
        int afterFirst = count("metric_sample");

        runner.run(null);

        assertThat(count("metric_sample")).isEqualTo(afterFirst);
    }

    @Test
    void secondRun_warningCountUnchanged() throws Exception {
        runner.run(null);
        int afterFirst = count("collection_warning");

        runner.run(null);

        assertThat(count("collection_warning")).isEqualTo(afterFirst);
    }

    @Test
    void secondRun_pausedCountUnchanged() throws Exception {
        runner.run(null);
        int afterFirst = count("appliance", "collection_state = 'PAUSED'");

        runner.run(null);

        assertThat(count("appliance", "collection_state = 'PAUSED'")).isEqualTo(afterFirst);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private int count(String table, String where) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE " + where, Integer.class);
    }
}

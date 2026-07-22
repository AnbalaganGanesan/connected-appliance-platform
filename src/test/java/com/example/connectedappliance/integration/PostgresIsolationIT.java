package com.example.connectedappliance.integration;

import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
@Transactional
class PostgresIsolationIT extends PostgresIntegrationTestSupport {

    private static final String TEST_TABLE = "task4_test_isolation_probe";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeAll
    void createEmptyTestTable() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + TEST_TABLE
                + " (probe_id uuid PRIMARY KEY)");
        jdbcTemplate.execute("TRUNCATE TABLE " + TEST_TABLE);
    }

    @AfterAll
    void dropTestTable() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + TEST_TABLE);
    }

    @Test
    void rollsBackTheFirstIndependentInsert() {
        verifyEmptyTableAndInsert(UUID.randomUUID());
    }

    @Test
    void rollsBackTheSecondIndependentInsert() {
        verifyEmptyTableAndInsert(UUID.randomUUID());
    }

    private void verifyEmptyTableAndInsert(UUID probeId) {
        assertThat(TestTransaction.isActive()).isTrue();
        assertThat(rowCount()).isZero();

        jdbcTemplate.update(
                "INSERT INTO " + TEST_TABLE + " (probe_id) VALUES (?)",
                probeId);

        Long insertedRowCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + TEST_TABLE + " WHERE probe_id = ?",
                Long.class,
                probeId);
        assertThat(insertedRowCount).isOne();
    }

    private long rowCount() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + TEST_TABLE,
                Long.class);
        return count == null ? 0 : count;
    }
}

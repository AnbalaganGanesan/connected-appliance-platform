package com.example.connectedappliance.shared.error;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemDetailFactoryTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-07-22T08:15:30Z");

    private final ValidationErrorMapper validationErrorMapper =
            new ValidationErrorMapper(new ValidationCodeMapper());
    private final ProblemDetailFactory factory = new ProblemDetailFactory(
            Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC), validationErrorMapper);

    @Test
    void createsCompleteProblemUsingInjectedUtcClockAndPathOnly() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/example");
        request.setQueryString("sensitive=value");

        ProblemDetail problem = factory.create(
                CommonApiProblems.INVALID_CORRELATION_ID,
                request,
                "safe-correlation-id");

        assertThat(problem.getType().toString()).isEqualTo(
                "urn:connected-appliance-platform:problem:invalid-correlation-id");
        assertThat(problem.getTitle()).isEqualTo("Invalid correlation ID");
        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getDetail()).isEqualTo(
                "X-Correlation-ID must contain 1 to 64 letters, digits, periods, underscores, or hyphens.");
        assertThat(problem.getInstance().toString()).isEqualTo("/api/v1/example");
        assertThat(problem.getProperties())
                .containsEntry("code", "INVALID_CORRELATION_ID")
                .containsEntry("correlationId", "safe-correlation-id")
                .containsEntry("timestamp", FIXED_INSTANT)
                .doesNotContainKey("errors");
    }

    @Test
    @SuppressWarnings("unchecked")
    void ordersValidationItemsWithoutRejectedValues() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/v1/example");
        List<ValidationItem> unsorted = List.of(
                new ValidationItem("quantity", "OUT_OF_RANGE", "must be within the allowed range"),
                new ValidationItem("name", "REQUIRED", "is required"));

        ProblemDetail problem = factory.createValidation(
                request, "safe-correlation-id", unsorted);
        List<ValidationItem> errors =
                (List<ValidationItem>) problem.getProperties().get("errors");

        assertThat(errors).containsExactly(
                new ValidationItem("name", "REQUIRED", "is required"),
                new ValidationItem(
                        "quantity",
                        "OUT_OF_RANGE",
                        "must be within the allowed range"));
        assertThat(errors)
                .allSatisfy(item -> assertThat(item)
                        .extracting(
                                ValidationItem::field,
                                ValidationItem::code,
                                ValidationItem::message)
                        .doesNotContain("rejected-secret"));
    }
}

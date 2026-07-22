package com.example.task5fixture;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.connectedappliance.shared.observability.CorrelationIdConstants;

@Validated
@RestController
@RequestMapping("/test/task5")
public class Task5FixtureController {

    public static final String SENSITIVE_FAILURE_MARKER =
            "task5-sensitive-internal-marker SQL password internal_table IllegalStateException";

    private static final AtomicInteger INVOCATIONS = new AtomicInteger();

    public static void resetInvocations() {
        INVOCATIONS.set(0);
    }

    public static int invocations() {
        return INVOCATIONS.get();
    }

    @GetMapping("/success")
    Map<String, String> success(HttpServletRequest request) {
        INVOCATIONS.incrementAndGet();
        return Map.of(
                "correlationId", MDC.get(CorrelationIdConstants.MDC_KEY),
                "requestAttribute", String.valueOf(
                        request.getAttribute(CorrelationIdConstants.REQUEST_ATTRIBUTE)));
    }

    @PostMapping("/validated")
    ResponseEntity<Void> validated(@Valid @RequestBody Task5FixtureRequest request) {
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/parameter")
    ResponseEntity<Void> parameter(
            @RequestParam @Min(1) @Max(10) Integer quantity) {
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/failure")
    ResponseEntity<Void> failure() {
        throw new IllegalStateException(SENSITIVE_FAILURE_MARKER);
    }

    public record Task5FixtureRequest(
            @NotBlank String name,
            @NotNull @Min(1) @Max(10) Integer quantity) {
    }
}

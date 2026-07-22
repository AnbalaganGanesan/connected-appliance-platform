package com.example.connectedappliance.metrics.api;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.example.connectedappliance.metrics.domain.CollectionOutcome;
import com.example.connectedappliance.metrics.domain.CollectionTrigger;
import com.example.connectedappliance.shared.error.RequestValidationException;
import com.example.connectedappliance.shared.validation.UtcInstantQueryParser;
import org.springframework.util.MultiValueMap;

/** Strict parsing for the two approved Metrics history query parameter sets. */
final class MetricsHistoryQueryParameters {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final Set<String> ATTEMPT_PARAMETERS =
            Set.of("page", "size", "trigger", "outcome");
    private static final Set<String> METRIC_PARAMETERS =
            Set.of("from", "to", "page", "size");

    private MetricsHistoryQueryParameters() {
    }

    static AttemptParameters attempts(MultiValueMap<String, String> parameters) {
        rejectUnknown(parameters, ATTEMPT_PARAMETERS);
        return new AttemptParameters(
                integer(parameters, "page", DEFAULT_PAGE, 0, Integer.MAX_VALUE),
                integer(parameters, "size", DEFAULT_SIZE, 1, MAX_SIZE),
                optionalEnum(parameters, "trigger", CollectionTrigger.class),
                optionalEnum(parameters, "outcome", CollectionOutcome.class));
    }

    static MetricParameters metrics(
            MultiValueMap<String, String> parameters, UtcInstantQueryParser parser) {
        rejectUnknown(parameters, METRIC_PARAMETERS);
        return new MetricParameters(
                parser.parseRequired(optionalRaw(parameters, "from"), "from"),
                parser.parseRequired(optionalRaw(parameters, "to"), "to"),
                integer(parameters, "page", DEFAULT_PAGE, 0, Integer.MAX_VALUE),
                integer(parameters, "size", DEFAULT_SIZE, 1, MAX_SIZE));
    }

    private static void rejectUnknown(
            MultiValueMap<String, String> parameters, Set<String> allowed) {
        parameters.keySet().stream()
                .filter(parameter -> !allowed.contains(parameter))
                .sorted()
                .findFirst()
                .ifPresent(parameter -> {
                    throw invalid(parameter, "UNKNOWN_FIELD");
                });
    }

    private static int integer(
            MultiValueMap<String, String> parameters,
            String name,
            int defaultValue,
            int minimum,
            int maximum) {
        List<String> values = parameters.get(name);
        if (values == null) {
            return defaultValue;
        }
        String value = single(name, values);
        final int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw invalid(name, "INVALID_FORMAT");
        }
        if (parsed < minimum || parsed > maximum) {
            throw invalid(name, "OUT_OF_RANGE");
        }
        return parsed;
    }

    private static <E extends Enum<E>> Optional<E> optionalEnum(
            MultiValueMap<String, String> parameters, String name, Class<E> enumType) {
        List<String> values = parameters.get(name);
        if (values == null) {
            return Optional.empty();
        }
        String value = single(name, values);
        try {
            return Optional.of(Enum.valueOf(enumType, value));
        } catch (IllegalArgumentException exception) {
            throw invalid(name, "INVALID_FORMAT");
        }
    }

    private static String optionalRaw(MultiValueMap<String, String> parameters, String name) {
        List<String> values = parameters.get(name);
        return values == null ? null : single(name, values);
    }

    private static String single(String name, List<String> values) {
        if (values.size() != 1 || values.get(0) == null || values.get(0).isEmpty()) {
            throw invalid(name, "INVALID_FORMAT");
        }
        return values.get(0);
    }

    private static RequestValidationException invalid(String field, String code) {
        return new RequestValidationException(field, code);
    }

    record AttemptParameters(
            int page,
            int size,
            Optional<CollectionTrigger> trigger,
            Optional<CollectionOutcome> outcome) {
    }

    record MetricParameters(Instant from, Instant to, int page, int size) {
    }
}

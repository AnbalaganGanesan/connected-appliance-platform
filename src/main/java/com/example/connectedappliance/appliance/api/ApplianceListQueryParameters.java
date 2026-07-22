package com.example.connectedappliance.appliance.api;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.util.MultiValueMap;

import com.example.connectedappliance.appliance.domain.CollectionState;
import com.example.connectedappliance.shared.error.RequestValidationException;

/** Strict parser for the approved Appliance-list query parameters. */
record ApplianceListQueryParameters(
        int page, int size, Optional<CollectionState> collectionState) {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final Set<String> ALLOWED_PARAMETERS =
            Set.of("page", "size", "collectionState");

    static ApplianceListQueryParameters parse(MultiValueMap<String, String> parameters) {
        parameters.keySet().stream()
                .filter(parameter -> !ALLOWED_PARAMETERS.contains(parameter))
                .sorted()
                .findFirst()
                .ifPresent(parameter -> {
                    throw invalid(parameter, "UNKNOWN_FIELD");
                });

        int page = integerParameter(parameters, "page", DEFAULT_PAGE, 0, Integer.MAX_VALUE);
        int size = integerParameter(parameters, "size", DEFAULT_SIZE, 1, MAX_SIZE);
        Optional<CollectionState> state = optionalState(parameters);
        return new ApplianceListQueryParameters(page, size, state);
    }

    private static int integerParameter(
            MultiValueMap<String, String> parameters,
            String name,
            int defaultValue,
            int minimum,
            int maximum) {
        List<String> values = parameters.get(name);
        if (values == null) {
            return defaultValue;
        }

        String value = singleValue(name, values);
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

    private static Optional<CollectionState> optionalState(
            MultiValueMap<String, String> parameters) {
        List<String> values = parameters.get("collectionState");
        if (values == null) {
            return Optional.empty();
        }

        String value = singleValue("collectionState", values);
        try {
            return Optional.of(CollectionState.valueOf(value));
        } catch (IllegalArgumentException exception) {
            throw invalid("collectionState", "INVALID_FORMAT");
        }
    }

    private static String singleValue(String name, List<String> values) {
        if (values.size() != 1 || values.get(0) == null || values.get(0).isEmpty()) {
            throw invalid(name, "INVALID_FORMAT");
        }
        return values.get(0);
    }

    private static RequestValidationException invalid(String field, String code) {
        return new RequestValidationException(field, code);
    }
}

package com.example.connectedappliance.shared.observability;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Component;

@Component
public class CorrelationIdService {

    private static final Pattern VALID_CORRELATION_ID =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    public CorrelationIdResolution resolve(HttpServletRequest request) {
        List<String> suppliedValues = Collections.list(
                request.getHeaders(CorrelationIdConstants.HEADER_NAME));

        if (suppliedValues.isEmpty()) {
            return new CorrelationIdResolution(generate(), false);
        }

        if (suppliedValues.size() == 1 && isValid(suppliedValues.get(0))) {
            return new CorrelationIdResolution(suppliedValues.get(0), false);
        }

        return new CorrelationIdResolution(generate(), true);
    }

    public String generate() {
        return UUID.randomUUID().toString();
    }

    private boolean isValid(String value) {
        return value != null && VALID_CORRELATION_ID.matcher(value).matches();
    }
}

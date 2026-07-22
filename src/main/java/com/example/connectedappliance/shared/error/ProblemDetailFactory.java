package com.example.connectedappliance.shared.error;

import java.net.URI;
import java.time.Clock;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

@Component
public class ProblemDetailFactory {

    private final Clock clock;
    private final ValidationErrorMapper validationErrorMapper;

    public ProblemDetailFactory(Clock clock, ValidationErrorMapper validationErrorMapper) {
        this.clock = clock;
        this.validationErrorMapper = validationErrorMapper;
    }

    public ProblemDetail create(
            ApiProblemDefinition definition,
            HttpServletRequest request,
            String correlationId) {
        return create(definition, request, correlationId, List.of());
    }

    public ProblemDetail createValidation(
            HttpServletRequest request,
            String correlationId,
            List<ValidationItem> validationItems) {
        return create(
                CommonApiProblems.VALIDATION_ERROR,
                request,
                correlationId,
                validationItems);
    }

    private ProblemDetail create(
            ApiProblemDefinition definition,
            HttpServletRequest request,
            String correlationId,
            List<ValidationItem> validationItems) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                definition.status(), definition.detail());
        problem.setType(definition.type());
        problem.setTitle(definition.title());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", definition.code());
        problem.setProperty("correlationId", correlationId);
        problem.setProperty("timestamp", clock.instant());
        if (definition == CommonApiProblems.VALIDATION_ERROR) {
            problem.setProperty("errors", validationErrorMapper.sorted(validationItems));
        }
        return problem;
    }
}

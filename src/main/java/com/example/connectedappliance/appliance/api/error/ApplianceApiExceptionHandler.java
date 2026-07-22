package com.example.connectedappliance.appliance.api.error;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.connectedappliance.appliance.application.exception.ApplianceNotFoundException;
import com.example.connectedappliance.appliance.application.exception.DuplicateApplianceException;
import com.example.connectedappliance.appliance.application.exception.UnsupportedVendorException;
import com.example.connectedappliance.shared.error.ApiProblemDefinition;
import com.example.connectedappliance.shared.error.ProblemDetailFactory;
import com.example.connectedappliance.shared.observability.CorrelationIdConstants;
import com.example.connectedappliance.shared.observability.CorrelationIdService;

/** Maps Appliance application outcomes through the shared sanitized ProblemDetail factory. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class ApplianceApiExceptionHandler {

    private final ProblemDetailFactory problemDetailFactory;
    private final CorrelationIdService correlationIdService;

    public ApplianceApiExceptionHandler(
            ProblemDetailFactory problemDetailFactory,
            CorrelationIdService correlationIdService) {
        this.problemDetailFactory = problemDetailFactory;
        this.correlationIdService = correlationIdService;
    }

    @ExceptionHandler(ApplianceNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(HttpServletRequest request) {
        return response(ApplianceApiProblems.APPLIANCE_NOT_FOUND, request);
    }

    @ExceptionHandler(DuplicateApplianceException.class)
    ResponseEntity<ProblemDetail> handleDuplicate(HttpServletRequest request) {
        return response(ApplianceApiProblems.DUPLICATE_APPLIANCE, request);
    }

    @ExceptionHandler(UnsupportedVendorException.class)
    ResponseEntity<ProblemDetail> handleUnsupportedVendor(HttpServletRequest request) {
        return response(ApplianceApiProblems.UNSUPPORTED_VENDOR, request);
    }

    private ResponseEntity<ProblemDetail> response(
            ApiProblemDefinition definition, HttpServletRequest request) {
        Object requestCorrelationId = request.getAttribute(CorrelationIdConstants.REQUEST_ATTRIBUTE);
        String correlationId = requestCorrelationId instanceof String value
                ? value
                : correlationIdService.generate();
        ProblemDetail problem = problemDetailFactory.create(definition, request, correlationId);
        return ResponseEntity.status(definition.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}

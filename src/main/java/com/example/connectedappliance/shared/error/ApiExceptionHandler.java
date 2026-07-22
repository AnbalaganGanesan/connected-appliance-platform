package com.example.connectedappliance.shared.error;

import java.util.List;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.converter.HttpMessageNotReadableException;

import com.example.connectedappliance.shared.observability.CorrelationIdConstants;
import com.example.connectedappliance.shared.observability.CorrelationIdService;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final ProblemDetailFactory problemDetailFactory;
    private final ValidationErrorMapper validationErrorMapper;
    private final CorrelationIdService correlationIdService;

    public ApiExceptionHandler(
            ProblemDetailFactory problemDetailFactory,
            ValidationErrorMapper validationErrorMapper,
            CorrelationIdService correlationIdService) {
        this.problemDetailFactory = problemDetailFactory;
        this.validationErrorMapper = validationErrorMapper;
        this.correlationIdService = correlationIdService;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApiException(
            ApiException exception, HttpServletRequest request) {
        return response(exception.problem(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        return validationResponse(
                request,
                validationErrorMapper.fromBindingResult(exception.getBindingResult()));
    }

    @ExceptionHandler(BindException.class)
    ResponseEntity<ProblemDetail> handleBind(
            BindException exception, HttpServletRequest request) {
        return validationResponse(
                request,
                validationErrorMapper.fromBindingResult(exception.getBindingResult()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraintViolation(
            ConstraintViolationException exception, HttpServletRequest request) {
        return validationResponse(
                request,
                validationErrorMapper.fromConstraintViolations(
                        exception.getConstraintViolations()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
        return validationResponse(
                request,
                validationErrorMapper.single(exception.getName(), "INVALID_FORMAT"));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ProblemDetail> handleMissingParameter(
            MissingServletRequestParameterException exception, HttpServletRequest request) {
        return validationResponse(
                request,
                validationErrorMapper.single(exception.getParameterName(), "REQUIRED"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> handleUnreadableMessage(
            HttpMessageNotReadableException exception, HttpServletRequest request) {
        JsonParseException syntaxFailure = findCause(exception, JsonParseException.class);
        if (syntaxFailure != null) {
            return response(CommonApiProblems.MALFORMED_JSON, request);
        }

        UnrecognizedPropertyException unknownProperty =
                findCause(exception, UnrecognizedPropertyException.class);
        if (unknownProperty != null) {
            return validationResponse(
                    request,
                    validationErrorMapper.single(
                            unknownProperty.getPropertyName(), "UNKNOWN_FIELD"));
        }

        JsonMappingException mappingFailure = findCause(exception, JsonMappingException.class);
        String field = mappingFailure == null ? "request" : mappingField(mappingFailure);
        return validationResponse(
                request,
                validationErrorMapper.single(field, "INVALID_FORMAT"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ProblemDetail> handleNoResourceFound(HttpServletRequest request) {
        return response(CommonApiProblems.NOT_FOUND, request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(
            Exception exception, HttpServletRequest request) {
        String correlationId = correlationId(request);
        LOGGER.error(
                "Request failed category={} method={} path={} status={} correlationId={}",
                CommonApiProblems.INTERNAL_ERROR.code(),
                request.getMethod(),
                request.getRequestURI(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                correlationId);
        return response(CommonApiProblems.INTERNAL_ERROR, request, correlationId);
    }

    private ResponseEntity<ProblemDetail> validationResponse(
            HttpServletRequest request, List<ValidationItem> validationItems) {
        String correlationId = correlationId(request);
        ProblemDetail problem = problemDetailFactory.createValidation(
                request, correlationId, validationItems);
        return response(problem, HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<ProblemDetail> response(
            ApiProblemDefinition definition, HttpServletRequest request) {
        return response(definition, request, correlationId(request));
    }

    private ResponseEntity<ProblemDetail> response(
            ApiProblemDefinition definition,
            HttpServletRequest request,
            String correlationId) {
        ProblemDetail problem = problemDetailFactory.create(definition, request, correlationId);
        return response(problem, definition.status());
    }

    private ResponseEntity<ProblemDetail> response(
            ProblemDetail problem, HttpStatus status) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private String correlationId(HttpServletRequest request) {
        Object correlationId = request.getAttribute(CorrelationIdConstants.REQUEST_ATTRIBUTE);
        return correlationId instanceof String value ? value : correlationIdService.generate();
    }

    private String mappingField(JsonMappingException exception) {
        return exception.getPath().stream()
                .map(JsonMappingException.Reference::getFieldName)
                .filter(field -> field != null && !field.isBlank())
                .reduce((first, second) -> first + "." + second)
                .orElse("request");
    }

    private <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }
}

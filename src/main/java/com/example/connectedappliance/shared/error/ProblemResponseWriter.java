package com.example.connectedappliance.shared.error;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

@Component
public class ProblemResponseWriter {

    private final ObjectMapper objectMapper;
    private final ProblemDetailFactory problemDetailFactory;

    public ProblemResponseWriter(
            ObjectMapper objectMapper,
            ProblemDetailFactory problemDetailFactory) {
        this.objectMapper = objectMapper;
        this.problemDetailFactory = problemDetailFactory;
    }

    public void write(
            ApiProblemDefinition definition,
            HttpServletRequest request,
            HttpServletResponse response,
            String correlationId) throws IOException {
        ProblemDetail problem = problemDetailFactory.create(definition, request, correlationId);
        response.setStatus(definition.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}

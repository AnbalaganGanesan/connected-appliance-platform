package com.example.connectedappliance.shared.observability;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.connectedappliance.shared.error.CommonApiProblems;
import com.example.connectedappliance.shared.error.ProblemResponseWriter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(CorrelationIdFilter.class);

    private final CorrelationIdService correlationIdService;
    private final ProblemResponseWriter problemResponseWriter;

    public CorrelationIdFilter(
            CorrelationIdService correlationIdService,
            ProblemResponseWriter problemResponseWriter) {
        this.correlationIdService = correlationIdService;
        this.problemResponseWriter = problemResponseWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        CorrelationIdResolution resolution = correlationIdService.resolve(request);
        String correlationId = resolution.effectiveId();

        response.setHeader(CorrelationIdConstants.HEADER_NAME, correlationId);
        request.setAttribute(CorrelationIdConstants.REQUEST_ATTRIBUTE, correlationId);
        MDC.put(CorrelationIdConstants.MDC_KEY, correlationId);

        try {
            if (resolution.rejected()) {
                LOGGER.warn(
                        "Rejected request correlation metadata method={} path={} status={} correlationId={}",
                        request.getMethod(),
                        request.getRequestURI(),
                        HttpServletResponse.SC_BAD_REQUEST,
                        correlationId);
                problemResponseWriter.write(
                        CommonApiProblems.INVALID_CORRELATION_ID,
                        request,
                        response,
                        correlationId);
                return;
            }

            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(CorrelationIdConstants.MDC_KEY);
        }
    }
}

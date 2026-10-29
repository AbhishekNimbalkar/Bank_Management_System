package com.bankmanagement.common.security;

import com.bankmanagement.common.exception.ErrorCode;
import com.bankmanagement.common.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Services accept traffic only from the API gateway or from sibling services. Both attach a shared
 * internal token; a request without it (e.g. someone calling a service port directly, bypassing
 * the gateway's CORS, rate limiting and header sanitising) is rejected before authentication.
 * Container health probes and the public signing-key document are the only exceptions.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class GatewayOnlyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Token";
    private static final int MIN_TOKEN_LENGTH = 32;
    private static final List<String> EXEMPT_PREFIXES = List.of("/actuator/health", "/.well-known/jwks.json");

    private final byte[] expected;
    private final ObjectMapper mapper;

    public GatewayOnlyFilter(@Value("${app.security.internal-token:${INTERNAL_API_TOKEN:}}") String token,
                             ObjectMapper mapper) {
        if (token == null || token.length() < MIN_TOKEN_LENGTH) {
            throw new IllegalStateException("app.security.internal-token (INTERNAL_API_TOKEN) must be set to a random "
                    + "value of at least " + MIN_TOKEN_LENGTH + " characters.");
        }
        this.expected = token.getBytes(StandardCharsets.UTF_8);
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return EXEMPT_PREFIXES.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String presented = request.getHeader(HEADER);
        boolean trusted = presented != null
                && MessageDigest.isEqual(expected, presented.getBytes(StandardCharsets.UTF_8));
        if (!trusted) {
            log.warn("Rejected direct (non-gateway) call to {} {} from {}", request.getMethod(),
                    request.getRequestURI(), request.getRemoteAddr());
            response.setStatus(ErrorCode.FORBIDDEN.status().value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            mapper.writeValue(response.getOutputStream(), GlobalExceptionHandler.build(ErrorCode.FORBIDDEN,
                    "Direct access to this service is not allowed. Use the API gateway.", null).getBody());
            return;
        }
        chain.doFilter(request, response);
    }
}

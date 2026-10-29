package com.bankmanagement.common.security;

import com.bankmanagement.common.exception.ErrorCode;
import com.bankmanagement.common.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Stateless resource-server setup shared by every service: bearer JWTs only, no sessions, no CSRF
 * (no cookies are used), method-level authorization, and JSON problem responses for 401/403.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class ResourceServerSecurityConfig {

    private static final String[] INFRA_PATHS = {
            "/actuator/health/**", "/actuator/info",
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper mapper,
                                                   @Value("${app.security.public-paths:}") List<String> publicPaths)
            throws Exception {
        List<String> open = new ArrayList<>(List.of(INFRA_PATHS));
        publicPaths.stream().filter(p -> !p.isBlank()).forEach(open::add);

        http.csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives("frame-ancestors 'none'"))
                        .frameOptions(f -> f.deny()))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(open.toArray(String[]::new)).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new JwtUserConverter()))
                        .authenticationEntryPoint(entryPoint(mapper))
                        .accessDeniedHandler(accessDenied(mapper)))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(entryPoint(mapper))
                        .accessDeniedHandler(accessDenied(mapper)));
        return http.build();
    }

    private AuthenticationEntryPoint entryPoint(ObjectMapper mapper) {
        return (request, response, ex) -> write(response, mapper, ErrorCode.UNAUTHENTICATED,
                "A valid access token is required.");
    }

    private AccessDeniedHandler accessDenied(ObjectMapper mapper) {
        return (request, response, ex) -> write(response, mapper, ErrorCode.FORBIDDEN,
                "You do not have permission to perform this action.");
    }

    private static void write(HttpServletResponse response, ObjectMapper mapper, ErrorCode code, String detail)
            throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), GlobalExceptionHandler.build(code, detail, null).getBody());
    }
}

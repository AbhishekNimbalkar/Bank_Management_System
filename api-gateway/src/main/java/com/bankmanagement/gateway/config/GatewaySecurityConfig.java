package com.bankmanagement.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter;
import org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * Edge security. Tokens are verified here (defence in depth) and again in every service, so a
 * request that bypasses the gateway is still rejected.
 */
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    /** The only endpoints reachable without a token: sign-in, token refresh/sign-out and liveness. */
    private static final String[] PUBLIC_PATHS = {
            "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout", "/actuator/health/**"
    };

    /** API documentation; public only when SWAGGER_ENABLED=true (development), otherwise denied. */
    private static final String[] DOC_PATHS = {
            "/docs/**", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/webjars/**"
    };

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http,
                                                            @Value("${app.docs.enabled:false}") boolean docsEnabled) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .cors(Customizer.withDefaults())
                .headers(h -> h
                        .hsts(hsts -> hsts.includeSubdomains(true).maxAge(Duration.ofDays(365)))
                        .referrerPolicy(r -> r.policy(ReferrerPolicyServerHttpHeadersWriter.ReferrerPolicy.NO_REFERRER))
                        .frameOptions(f -> f.mode(XFrameOptionsServerHttpHeadersWriter.Mode.DENY)))
                .authorizeExchange(ex -> ex
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .pathMatchers(PUBLIC_PATHS).permitAll()
                        .pathMatchers(DOC_PATHS).access((auth, ctx) -> Mono.just(new AuthorizationDecision(docsEnabled)))
                        .anyExchange().authenticated())
                .oauth2ResourceServer(rs -> rs.jwt(Customizer.withDefaults()))
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:5173}") List<String> allowedOrigins) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(allowedOrigins);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id", "X-Tenant-Code"));
        cors.setExposedHeaders(List.of("X-Request-Id"));
        cors.setAllowCredentials(false);
        cors.setMaxAge(Duration.ofHours(1));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}

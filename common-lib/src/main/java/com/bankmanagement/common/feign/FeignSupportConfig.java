package com.bankmanagement.common.feign;

import com.bankmanagement.common.exception.BusinessException;
import com.bankmanagement.common.exception.ErrorCode;
import com.bankmanagement.common.security.GatewayOnlyFilter;
import com.bankmanagement.common.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Value;
import com.bankmanagement.common.web.RequestIdFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.RequestInterceptor;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;

import java.io.InputStream;
import java.util.Arrays;

/**
 * Service-to-service calls run with the caller's own token (no shared super-credential), so the
 * downstream service applies the same permission checks as for a direct call.
 */
@Slf4j
@Configuration
public class FeignSupportConfig {

    @Bean
    public RequestInterceptor forwardCallerContext(
            @Value("${app.security.internal-token:${INTERNAL_API_TOKEN:}}") String internalToken) {
        return template -> {
            template.header(GatewayOnlyFilter.HEADER, internalToken);
            SecurityUtils.currentRequest().ifPresent(request -> {
                String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
                if (auth != null && !template.headers().containsKey(HttpHeaders.AUTHORIZATION)) {
                    template.header(HttpHeaders.AUTHORIZATION, auth);
                }
                String tenant = request.getHeader(SecurityUtils.TENANT_HEADER);
                if (tenant != null && !template.headers().containsKey(SecurityUtils.TENANT_HEADER)) {
                    template.header(SecurityUtils.TENANT_HEADER, tenant);
                }
            });
            String requestId = MDC.get(RequestIdFilter.MDC_KEY);
            if (requestId != null) {
                template.header(RequestIdFilter.HEADER, requestId);
            }
        };
    }

    @Bean
    public ErrorDecoder problemDetailErrorDecoder(ObjectMapper mapper) {
        return (methodKey, response) -> {
            String detail = readDetail(mapper, response);
            ErrorCode code = Arrays.stream(ErrorCode.values())
                    .filter(c -> c.status().value() == response.status())
                    .findFirst()
                    .orElse(ErrorCode.DEPENDENCY_FAILURE);
            if (response.status() >= 500) {
                log.error("Downstream call {} failed with {}", methodKey, response.status());
                return new BusinessException(ErrorCode.DEPENDENCY_FAILURE,
                        "A dependent service is unavailable. Please try again shortly.");
            }
            return new BusinessException(code, detail != null ? detail : "Downstream request rejected.");
        };
    }

    private static String readDetail(ObjectMapper mapper, Response response) {
        if (response.body() == null) {
            return null;
        }
        try (InputStream in = response.body().asInputStream()) {
            JsonNode node = mapper.readTree(in);
            return node.hasNonNull("detail") ? node.get("detail").asText() : null;
        } catch (Exception ex) {
            return null;
        }
    }
}

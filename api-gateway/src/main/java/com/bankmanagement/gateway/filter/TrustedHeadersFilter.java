package com.bankmanagement.gateway.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Sanitises every proxied request:
 * <ul>
 *   <li>drops client-supplied forwarding headers, so callers cannot spoof their IP (audit, rate limits);
 *       the gateway adds the real peer address when proxying;</li>
 *   <li>drops any client-supplied internal token and stamps the real one, which downstream services
 *       require ({@code GatewayOnlyFilter}) - so services are reachable only through this gateway.</li>
 * </ul>
 */
@Component
public class TrustedHeadersFilter implements GlobalFilter, Ordered {

    static final String INTERNAL_HEADER = "X-Internal-Token";
    private static final List<String> UNTRUSTED = List.of("X-Forwarded-For", "X-Real-IP", "Forwarded",
            "X-Forwarded-Host", "X-Forwarded-Port", "X-Forwarded-Proto", INTERNAL_HEADER);

    private final String internalToken;

    public TrustedHeadersFilter(@Value("${INTERNAL_API_TOKEN:}") String internalToken) {
        if (internalToken == null || internalToken.length() < 32) {
            throw new IllegalStateException("INTERNAL_API_TOKEN must be set to a random value of at least 32 characters.");
        }
        this.internalToken = internalToken;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        HttpHeaders sanitised = new HttpHeaders();
        sanitised.putAll(request.getHeaders());
        UNTRUSTED.forEach(sanitised::remove);
        sanitised.set(INTERNAL_HEADER, internalToken);
        HttpHeaders readOnly = HttpHeaders.readOnlyHttpHeaders(sanitised);
        ServerHttpRequest decorated = new ServerHttpRequestDecorator(request) {
            @Override
            public HttpHeaders getHeaders() {
                return readOnly;
            }
        };
        return chain.filter(exchange.mutate().request(decorated).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}

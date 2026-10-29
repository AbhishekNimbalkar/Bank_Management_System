package com.bankmanagement.gateway.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed-window, per-IP throttle for the unauthenticated auth endpoints (credential stuffing guard).
 * In-memory, so it is per gateway instance; use the Redis RequestRateLimiter when scaling out.
 */
@Component
public class AuthRateLimitFilter implements GlobalFilter, Ordered {

    private static final String AUTH_PREFIX = "/api/v1/auth/";
    private static final long WINDOW_MS = 60_000;

    private final int limitPerMinute;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public AuthRateLimitFilter(@Value("${app.rate-limit.auth-per-minute:30}") int limitPerMinute) {
        this.limitPerMinute = limitPerMinute;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!exchange.getRequest().getPath().value().startsWith(AUTH_PREFIX)) {
            return chain.filter(exchange);
        }
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        String ip = remote == null ? "unknown" : remote.getAddress().getHostAddress();
        long now = System.currentTimeMillis();
        Window window = windows.compute(ip, (k, w) -> w == null || now - w.start > WINDOW_MS ? new Window(now) : w);
        if (window.count.incrementAndGet() > limitPerMinute) {
            return reject(exchange.getResponse());
        }
        return chain.filter(exchange);
    }

    private Mono<Void> reject(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        response.getHeaders().set("Retry-After", "60");
        byte[] body = ("{\"title\":\"Too Many Requests\",\"status\":429,\"code\":\"TOO_MANY_REQUESTS\","
                + "\"detail\":\"Too many attempts. Try again in a minute.\"}").getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    @Scheduled(fixedDelay = WINDOW_MS)
    void evictExpired() {
        long now = System.currentTimeMillis();
        windows.entrySet().removeIf(e -> now - e.getValue().start > WINDOW_MS);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private static final class Window {
        private final long start;
        private final AtomicInteger count = new AtomicInteger();

        private Window(long start) {
            this.start = start;
        }
    }
}

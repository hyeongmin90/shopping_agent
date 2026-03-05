package com.shopping.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;

/**
 * Global filter that ensures every request has an X-Request-ID header.
 * If the incoming request already has one, it is forwarded as-is.
 * Otherwise, a new UUID is generated and attached to both the request
 * (for downstream services) and the response (for clients).
 */
@Slf4j
@Component
public class RequestIdFilter implements GlobalFilter, Ordered {

    public static final String REQUEST_ID_HEADER = "X-Request-ID";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String requestId = Optional.ofNullable(
                exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER)
        ).filter(v -> !v.isBlank()).orElseGet(() -> UUID.randomUUID().toString());

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(REQUEST_ID_HEADER, requestId)
                .build();

        exchange.getResponse().getHeaders().set(REQUEST_ID_HEADER, requestId);

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}

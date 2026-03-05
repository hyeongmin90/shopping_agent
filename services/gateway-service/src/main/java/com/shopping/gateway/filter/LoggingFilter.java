package com.shopping.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global filter that logs incoming requests and outgoing responses
 * with method, path, status code, and elapsed time for observability.
 */
@Slf4j
@Component
public class LoggingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getURI().getPath();
        String requestId = exchange.getRequest().getHeaders().getFirst("X-Request-ID");

        log.info(">>> Gateway request: {} {} [requestId={}]", method, path, requestId);

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            long elapsed = System.currentTimeMillis() - startTime;
            int statusCode = exchange.getResponse().getStatusCode() != null
                    ? exchange.getResponse().getStatusCode().value()
                    : 0;
            log.info("<<< Gateway response: {} {} [status={}, elapsed={}ms, requestId={}]",
                    method, path, statusCode, elapsed, requestId);
        }));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}

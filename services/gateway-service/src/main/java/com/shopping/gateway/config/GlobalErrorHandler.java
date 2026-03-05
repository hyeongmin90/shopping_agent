package com.shopping.gateway.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Global error handler that returns JSON error responses
 * matching the Nginx 503 error page format.
 */
@Slf4j
@Order(-2)
@Component
@RequiredArgsConstructor
public class GlobalErrorHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        log.error("Gateway error: {} - {}", ex.getClass().getSimpleName(), ex.getMessage());

        HttpStatus status = determineStatus(ex);
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> errorBody = Map.of(
                "error", status.getReasonPhrase(),
                "code", status.value()
        );

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(errorBody);
        } catch (JsonProcessingException e) {
            bytes = "{\"error\":\"Internal Server Error\",\"code\":500}".getBytes();
        }

        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private HttpStatus determineStatus(Throwable ex) {
        String className = ex.getClass().getSimpleName();
        if (className.contains("TimeoutException") || className.contains("ConnectException")) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if (className.contains("ResponseStatusException")) {
            return HttpStatus.BAD_GATEWAY;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}

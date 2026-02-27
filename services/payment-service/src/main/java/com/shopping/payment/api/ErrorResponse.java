package com.shopping.payment.api;

import java.time.Instant;

public record ErrorResponse(
        String message,
        String error,
        Instant timestamp
) {
    public static ErrorResponse of(String message, String error) {
        return new ErrorResponse(message, error, Instant.now());
    }
}

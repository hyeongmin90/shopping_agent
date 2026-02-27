package com.shopping.payment.service;

public record CommandProcessingResult(
        String eventType,
        Object eventData
) {
}

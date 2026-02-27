package com.shopping.payment.messaging.model;

import java.time.Instant;
import java.util.UUID;

public record PaymentCapturedData(
        UUID orderId,
        UUID paymentId,
        Integer capturedAmount,
        Instant capturedAt
) {
}

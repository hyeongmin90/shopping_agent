package com.shopping.payment.messaging.model;

import java.time.Instant;
import java.util.UUID;

public record PaymentVoidedData(
        UUID orderId,
        UUID paymentId,
        String reason,
        Instant voidedAt
) {
}

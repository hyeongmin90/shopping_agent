package com.shopping.payment.messaging.model;

import java.time.Instant;
import java.util.UUID;

public record PaymentRefundedData(
        UUID orderId,
        UUID paymentId,
        UUID refundId,
        Integer refundedAmount,
        String reason,
        Instant refundedAt
) {
}

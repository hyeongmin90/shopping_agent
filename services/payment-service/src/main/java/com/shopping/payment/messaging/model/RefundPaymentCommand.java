package com.shopping.payment.messaging.model;

import java.util.UUID;

public record RefundPaymentCommand(
        UUID orderId,
        UUID paymentId,
        Integer refundAmount,
        String reason
) {
}

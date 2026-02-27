package com.shopping.payment.messaging.model;

import java.util.UUID;

public record VoidPaymentCommand(
        UUID orderId,
        UUID paymentId,
        String reason
) {
}

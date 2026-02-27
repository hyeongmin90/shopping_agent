package com.shopping.payment.messaging.model;

import java.util.UUID;

public record AuthorizePaymentCommand(
        UUID orderId,
        UUID userId,
        Integer amount,
        String currency,
        String paymentMethod
) {
}

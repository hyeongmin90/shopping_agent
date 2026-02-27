package com.shopping.payment.messaging.model;

import java.util.UUID;

public record PaymentAuthorizationFailedData(
        UUID orderId,
        String reason,
        String errorCode,
        UUID paymentId
) {
}

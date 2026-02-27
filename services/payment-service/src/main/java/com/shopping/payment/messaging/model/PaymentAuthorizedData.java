package com.shopping.payment.messaging.model;

import java.util.UUID;

public record PaymentAuthorizedData(
        UUID orderId,
        UUID paymentId,
        Integer authorizedAmount,
        String authorizationCode
) {
}

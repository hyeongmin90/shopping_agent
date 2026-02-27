package com.shopping.payment.messaging.model;

import java.util.UUID;

public record CapturePaymentCommand(
        UUID orderId,
        UUID paymentId,
        Integer captureAmount
) {
}

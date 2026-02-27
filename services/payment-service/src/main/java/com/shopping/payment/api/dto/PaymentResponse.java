package com.shopping.payment.api.dto;

import com.shopping.payment.domain.Payment;
import com.shopping.payment.domain.PaymentStatus;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID orderId,
        UUID userId,
        Integer amount,
        String currency,
        PaymentStatus status,
        String paymentMethod,
        String authorizationCode,
        String failureReason,
        String idempotencyKey,
        Integer version,
        Instant createdAt,
        Instant updatedAt
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getUserId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getPaymentMethod(),
                payment.getAuthorizationCode(),
                payment.getFailureReason(),
                payment.getIdempotencyKey(),
                payment.getVersion(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}

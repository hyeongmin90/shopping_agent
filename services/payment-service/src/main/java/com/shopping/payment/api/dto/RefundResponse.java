package com.shopping.payment.api.dto;

import com.shopping.payment.domain.Refund;
import com.shopping.payment.domain.RefundStatus;
import java.time.Instant;
import java.util.UUID;

public record RefundResponse(
        UUID id,
        UUID paymentId,
        UUID orderId,
        Integer amount,
        String reason,
        RefundStatus status,
        Instant createdAt
) {
    public static RefundResponse from(Refund refund) {
        return new RefundResponse(
                refund.getId(),
                refund.getPaymentId(),
                refund.getOrderId(),
                refund.getAmount(),
                refund.getReason(),
                refund.getStatus(),
                refund.getCreatedAt()
        );
    }
}

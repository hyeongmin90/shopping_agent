package com.shopping.payment.domain;

public enum PaymentStatus {
    PENDING,
    AUTHORIZED,
    CAPTURED,
    VOIDED,
    REFUNDED,
    FAILED
}

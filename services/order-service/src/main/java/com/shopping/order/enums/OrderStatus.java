package com.shopping.order.enums;

public enum OrderStatus {
    DRAFT,
    PENDING_APPROVAL,
    PLACED,
    INVENTORY_RESERVING,
    PAYMENT_AUTHORIZING,
    CONFIRMED,
    FAILED,
    CANCELLED,
    REFUND_REQUESTED,
    REFUNDED
}

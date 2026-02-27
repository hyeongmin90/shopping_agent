package com.shopping.order.entity;

import com.shopping.order.enums.OrderSagaStatus;
import com.shopping.order.enums.OrderStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "total_amount")
    private Integer totalAmount;

    private String currency;

    @Column(name = "shipping_address", columnDefinition = "jsonb")
    private String shippingAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "saga_status")
    private OrderSagaStatus sagaStatus;

    @Column(name = "reservation_id")
    private UUID reservationId;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Version
    private Integer version;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @PrePersist
    public void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (currency == null) {
            currency = "KRW";
        }
        if (totalAmount == null) {
            totalAmount = 0;
        }
        if (status == null) {
            status = OrderStatus.DRAFT;
        }
        if (sagaStatus == null) {
            sagaStatus = OrderSagaStatus.NONE;
        }
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

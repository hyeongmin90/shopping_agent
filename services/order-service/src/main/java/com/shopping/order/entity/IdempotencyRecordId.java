package com.shopping.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
@Embeddable
public class IdempotencyRecordId implements Serializable {

    @Column(name = "consumer_id")
    private String consumerId;

    @Column(name = "event_id")
    private UUID eventId;

    public IdempotencyRecordId(String consumerId, UUID eventId) {
        this.consumerId = consumerId;
        this.eventId = eventId;
    }
}

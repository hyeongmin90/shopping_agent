package com.shopping.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Embeddable
public class IdempotencyRecordId implements Serializable {

    @Column(name = "consumer_id", nullable = false, length = 100)
    private String consumerId;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    public IdempotencyRecordId(String consumerId, UUID eventId) {
        this.consumerId = consumerId;
        this.eventId = eventId;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof IdempotencyRecordId that)) {
            return false;
        }
        return Objects.equals(consumerId, that.consumerId) && Objects.equals(eventId, that.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(consumerId, eventId);
    }
}

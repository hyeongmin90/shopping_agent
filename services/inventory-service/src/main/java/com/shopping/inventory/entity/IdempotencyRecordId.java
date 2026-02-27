package com.shopping.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyRecordId implements Serializable {

    @Column(name = "consumer_id", nullable = false, length = 100)
    private String consumerId;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;
}

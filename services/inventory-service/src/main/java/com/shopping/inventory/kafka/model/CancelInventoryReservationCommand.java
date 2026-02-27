package com.shopping.inventory.kafka.model;

import java.util.UUID;
import lombok.Data;

@Data
public class CancelInventoryReservationCommand {
    private UUID orderId;
    private UUID reservationId;
    private String reason;
}

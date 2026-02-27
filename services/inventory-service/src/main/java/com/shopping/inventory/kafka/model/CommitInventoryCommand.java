package com.shopping.inventory.kafka.model;

import java.util.UUID;
import lombok.Data;

@Data
public class CommitInventoryCommand {
    private UUID orderId;
    private UUID reservationId;
}

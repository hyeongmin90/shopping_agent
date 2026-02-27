package com.shopping.inventory.kafka.model;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import lombok.Data;

@Data
public class ReserveInventoryCommand {
    @NotNull
    private UUID orderId;

    @NotEmpty
    private List<ReserveInventoryItemCommand> items;
}

package com.shopping.order.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateOrderRequest {

    @NotNull
    private UUID userId;

    private String currency;

    private String idempotencyKey;
}

package com.shopping.order.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RefundRequest {
    private Integer amount;
    private String reason;
}

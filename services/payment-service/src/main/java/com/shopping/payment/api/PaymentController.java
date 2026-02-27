package com.shopping.payment.api;

import com.shopping.payment.api.dto.PaymentResponse;
import com.shopping.payment.api.dto.RefundResponse;
import com.shopping.payment.service.PaymentQueryService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentQueryService paymentQueryService;

    @GetMapping("/order/{orderId}")
    public PaymentResponse getPaymentByOrder(@PathVariable UUID orderId) {
        return PaymentResponse.from(paymentQueryService.getPaymentByOrderId(orderId));
    }

    @GetMapping("/{id}")
    public PaymentResponse getPaymentById(@PathVariable UUID id) {
        return PaymentResponse.from(paymentQueryService.getPaymentById(id));
    }

    @GetMapping("/{id}/refunds")
    public List<RefundResponse> getRefunds(@PathVariable UUID id) {
        return paymentQueryService.getRefundsByPaymentId(id)
                .stream()
                .map(RefundResponse::from)
                .toList();
    }
}

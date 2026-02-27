package com.shopping.payment.service;

import com.shopping.payment.domain.Payment;
import com.shopping.payment.domain.Refund;
import com.shopping.payment.exception.ResourceNotFoundException;
import com.shopping.payment.repository.PaymentRepository;
import com.shopping.payment.repository.RefundRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentQueryService {

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;

    @Transactional(readOnly = true)
    public Payment getPaymentById(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));
    }

    @Transactional(readOnly = true)
    public Payment getPaymentByOrderId(UUID orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for order: " + orderId));
    }

    @Transactional(readOnly = true)
    public List<Refund> getRefundsByPaymentId(UUID paymentId) {
        Payment payment = getPaymentById(paymentId);
        return refundRepository.findByPaymentIdOrderByCreatedAtAsc(payment.getId());
    }
}

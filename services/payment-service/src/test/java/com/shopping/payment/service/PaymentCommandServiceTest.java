package com.shopping.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shopping.payment.domain.Payment;
import com.shopping.payment.domain.PaymentStatus;
import com.shopping.payment.messaging.model.AuthorizePaymentCommand;
import com.shopping.payment.messaging.model.CapturePaymentCommand;
import com.shopping.payment.messaging.model.PaymentAuthorizedData;
import com.shopping.payment.messaging.model.PaymentCapturedData;
import com.shopping.payment.repository.PaymentRepository;
import com.shopping.payment.repository.RefundRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentCommandServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private MockPaymentGateway mockPaymentGateway;

    @InjectMocks
    private PaymentCommandService paymentCommandService;

    @Test
    @DisplayName("결제 승인 성공 처리")
    void handleAuthorize_Success() {
        // given
        UUID orderId = UUID.randomUUID();
        String userId = "test-user-id";
        AuthorizePaymentCommand command = new AuthorizePaymentCommand(orderId, userId, 1000, "KRW", "CARD");

        MockPaymentGateway.GatewayResult gatewayResult = new MockPaymentGateway.GatewayResult(
                MockPaymentGateway.GatewayOutcome.APPROVED, "AUTH-123", null);

        when(mockPaymentGateway.authorize(anyInt())).thenReturn(gatewayResult);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        // when
        CommandProcessingResult result = paymentCommandService.handleAuthorize(command, "idemp-1");

        // then
        assertThat(result.eventType()).isEqualTo("PaymentAuthorized");
        PaymentAuthorizedData data = (PaymentAuthorizedData) result.eventData();
        assertThat(data.orderId()).isEqualTo(orderId);
        assertThat(data.authorizationCode()).isEqualTo("AUTH-123");
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    @DisplayName("결제 캡처 처리 성공")
    void handleCapture_Success() {
        // given
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Payment payment = new Payment();
        payment.setId(paymentId);
        payment.setOrderId(orderId);
        payment.setAmount(1000);
        payment.setStatus(PaymentStatus.AUTHORIZED);

        CapturePaymentCommand command = new CapturePaymentCommand(orderId, paymentId, 1000);

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        // when
        CommandProcessingResult result = paymentCommandService.handleCapture(command);

        // then
        assertThat(result.eventType()).isEqualTo("PaymentCaptured");
        PaymentCapturedData data = (PaymentCapturedData) result.eventData();
        assertThat(data.paymentId()).isEqualTo(paymentId);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        verify(paymentRepository).save(payment);
    }
}

package com.shopping.payment.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopping.payment.messaging.model.AuthorizePaymentCommand;
import com.shopping.payment.service.CommandProcessingResult;
import com.shopping.payment.service.IdempotencyService;
import com.shopping.payment.service.PaymentCommandService;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentCommandConsumerTest {

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private PaymentCommandService paymentCommandService;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private PaymentEventProducer paymentEventProducer;

    @InjectMocks
    private PaymentCommandConsumer paymentCommandConsumer;

    @Test
    @DisplayName("결제 승인 커맨드 메시지 수신 처리")
    void onMessage_AuthorizeCommand() throws Exception {
        // given
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String payload = String.format(
                "{\"meta\": {\"eventType\": \"AuthorizePaymentCommand\", \"eventId\": \"%s\"}, \"data\": {\"orderId\": \"%s\", \"amount\": 1000}}",
                eventId, orderId);

        when(idempotencyService.isProcessed(any(), any())).thenReturn(false);
        when(paymentCommandService.handleAuthorize(any(AuthorizePaymentCommand.class), any()))
                .thenReturn(new CommandProcessingResult("PaymentAuthorized", null));

        // when
        paymentCommandConsumer.consume(payload);

        // then
        verify(paymentCommandService).handleAuthorize(any(AuthorizePaymentCommand.class), any());
        verify(idempotencyService).markProcessed(any(), any());
    }
}

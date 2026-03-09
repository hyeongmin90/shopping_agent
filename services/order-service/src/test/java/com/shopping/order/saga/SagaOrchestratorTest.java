package com.shopping.order.saga;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopping.order.repository.IdempotencyRecordRepository;
import com.shopping.order.repository.OrderRepository;
import com.shopping.order.service.OrderService;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SagaOrchestratorTest {

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private OrderService orderService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @InjectMocks
    private SagaOrchestrator sagaOrchestrator;

    @Test
    @DisplayName("재고 예약 성공 이벤트 처리")
    void onInventoryReserved_Success() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        String payload = String.format(
                "{\"meta\": {\"eventType\": \"InventoryReserved\", \"eventId\": \"%s\"}, \"data\": {\"orderId\": \"%s\", \"reservationId\": \"%s\"}}",
                eventId, orderId, reservationId);

        when(idempotencyRecordRepository.existsById(any())).thenReturn(false);

        // when
        sagaOrchestrator.onInventoryEvents(payload);

        // then
        verify(orderService).moveToPaymentAuthorizing(eq(orderId), eq(reservationId));
        verify(idempotencyRecordRepository).save(any());
    }

    @Test
    @DisplayName("결제 인증 성공 이벤트 처리")
    void onPaymentAuthorized_Success() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        String payload = String.format(
                "{\"meta\": {\"eventType\": \"PaymentAuthorized\", \"eventId\": \"%s\"}, \"data\": {\"orderId\": \"%s\", \"paymentId\": \"%s\"}}",
                eventId, orderId, paymentId);

        when(idempotencyRecordRepository.existsById(any())).thenReturn(false);

        // when
        sagaOrchestrator.onPaymentEvents(payload);

        // then
        verify(orderService).handlePaymentAuthorized(eq(orderId), eq(paymentId));
    }
}

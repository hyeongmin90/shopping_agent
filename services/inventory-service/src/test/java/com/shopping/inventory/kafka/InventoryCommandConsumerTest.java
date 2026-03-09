package com.shopping.inventory.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopping.inventory.kafka.model.ReserveInventoryCommand;
import com.shopping.inventory.service.IdempotencyService;
import com.shopping.inventory.service.InventoryCommandService;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InventoryCommandConsumerTest {

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private InventoryCommandService inventoryCommandService;

    @Mock
    private IdempotencyService idempotencyService;

    @InjectMocks
    private InventoryCommandConsumer inventoryCommandConsumer;

    @Test
    @DisplayName("재고 예약 커맨드 메시지 수신 처리")
    void onMessage_ReserveCommand() throws Exception {
        // given
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String payload = String.format(
                "{\"meta\": {\"eventType\": \"ReserveInventoryCommand\", \"eventId\": \"%s\"}, \"data\": {\"orderId\": \"%s\", \"items\": []}}",
                eventId, orderId);

        ReflectionTestUtils.setField(inventoryCommandConsumer, "reservationTtlMinutes", 10);
        when(idempotencyService.isProcessed(eq("inventory-service"), eq(eventId))).thenReturn(false);

        // when
        inventoryCommandConsumer.onMessage(payload);

        // then
        verify(inventoryCommandService).handleReserve(any(ReserveInventoryCommand.class), any(), anyInt());
        verify(idempotencyService).markProcessed(eq("inventory-service"), eq(eventId));
    }
}

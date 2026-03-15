package com.shopping.review.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OutboxService outboxService;

    @Test
    @DisplayName("아웃박스 이벤트 적재")
    void enqueue() throws Exception {
        UUID aggregateId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        UUID causationId = UUID.randomUUID();
        String idempotencyKey = "review-created-" + aggregateId;

        when(objectMapper.writeValueAsString(any())).thenReturn("{\"payload\":true}");

        outboxService.enqueue(
                "Review",
                aggregateId,
                "ReviewCreatedEvent",
                Map.of("reviewId", aggregateId),
                correlationId,
                causationId,
                idempotencyKey);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());

        OutboxEvent saved = captor.getValue();
        assertThat(saved.getAggregateType()).isEqualTo("Review");
        assertThat(saved.getAggregateId()).isEqualTo(aggregateId);
        assertThat(saved.getEventType()).isEqualTo("ReviewCreatedEvent");
        assertThat(saved.getPayload()).isEqualTo("{\"payload\":true}");
        assertThat(saved.getCorrelationId()).isEqualTo(correlationId);
        assertThat(saved.getCausationId()).isEqualTo(causationId);
        assertThat(saved.getIdempotencyKey()).isEqualTo(idempotencyKey);
    }
}

package com.shopping.review.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private OutboxPublisher outboxPublisher;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(outboxPublisher, "batchSize", 100);
        ReflectionTestUtils.setField(outboxPublisher, "reviewEventsTopic", "review.events");
    }

    @Test
    @DisplayName("미발행 아웃박스 이벤트 발행")
    void publishPendingEvents() {
        UUID reviewId = UUID.randomUUID();
        OutboxEvent event = new OutboxEvent();
        event.setAggregateId(reviewId);
        event.setPayload("{\"reviewId\":\"" + reviewId + "\"}");
        event.setPublished(false);
        event.setCreatedAt(LocalDateTime.now());

        when(outboxEventRepository.findByPublishedFalseOrderByCreatedAtAsc(any(Pageable.class)))
                .thenReturn(List.of(event));

        outboxPublisher.publishPendingEvents();

        verify(kafkaTemplate).send("review.events", reviewId.toString(), event.getPayload());
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().isPublished()).isTrue();
        assertThat(captor.getValue().getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("미발행 아웃박스 이벤트 없음")
    void publishPendingEvents_noEvents() {
        when(outboxEventRepository.findByPublishedFalseOrderByCreatedAtAsc(any(Pageable.class)))
                .thenReturn(List.of());

        outboxPublisher.publishPendingEvents();

        verify(kafkaTemplate, never()).send(any(), any(), any());
        verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
    }
}

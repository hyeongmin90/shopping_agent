package com.shopping.inventory.service;

import com.shopping.inventory.entity.OutboxEvent;
import com.shopping.inventory.repository.OutboxEventRepository;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Tracer tracer;

    @Value("${app.kafka.topics.events}")
    private String eventsTopic;

    @Value("${app.outbox.batch-size:100}")
    private int batchSize;

    @Transactional
    @Scheduled(fixedDelayString = "${app.outbox.publish-interval-ms:2000}")
    public void publishPendingEvents() {
        List<OutboxEvent> events = outboxEventRepository.findByPublishedFalseOrderByCreatedAtAsc(PageRequest.of(0, batchSize));
        for (OutboxEvent event : events) {
            publishWithTrace(event);
        }
        if (!events.isEmpty()) {
            log.info("Published {} outbox events", events.size());
        }
    }

    private void publishWithTrace(OutboxEvent event) {
        Span span = buildSpan(event);
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            kafkaTemplate.send(eventsTopic, event.getAggregateId(), event.getPayload());
            event.setPublished(true);
            event.setPublishedAt(LocalDateTime.now());
            outboxEventRepository.save(event);
        } finally {
            span.end();
        }
    }

    private Span buildSpan(OutboxEvent event) {
        Tracer.SpanBuilder builder = tracer.spanBuilder()
                .name("outbox publish " + event.getEventType())
                .kind(Span.Kind.PRODUCER)
                .tag("messaging.system", "kafka")
                .tag("messaging.destination", eventsTopic)
                .tag("messaging.event_type", event.getEventType());

        String traceparent = event.getTraceparent();
        if (traceparent != null) {
            try {
                String[] parts = traceparent.split("-");
                boolean sampled = "01".equals(parts[3]);
                builder.setParent(tracer.traceContextBuilder()
                        .traceId(parts[1])
                        .spanId(parts[2])
                        .sampled(sampled)
                        .build());
            } catch (Exception e) {
                log.warn("Failed to restore traceparent: {}", traceparent);
            }
        }

        return builder.start();
    }
}

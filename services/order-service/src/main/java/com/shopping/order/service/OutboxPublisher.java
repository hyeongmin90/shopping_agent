package com.shopping.order.service;

import com.shopping.order.entity.OutboxEvent;
import com.shopping.order.repository.OutboxEventRepository;
import com.shopping.order.support.TopicRouter;
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
    private final TopicRouter topicRouter;

    @Value("${app.outbox.batch-size:100}")
    private int batchSize;

    @Transactional
    @Scheduled(fixedDelayString = "${app.outbox.publish-interval-ms:2000}")
    public void publishPendingEvents() {
        List<OutboxEvent> events = outboxEventRepository.findByPublishedFalseOrderByCreatedAtAsc(PageRequest.of(0, batchSize));
        for (OutboxEvent event : events) {
            String topic = topicRouter.route(event.getEventType());
            kafkaTemplate.send(topic, event.getAggregateId().toString(), event.getPayload());
            event.setPublished(true);
            event.setPublishedAt(LocalDateTime.now());
            outboxEventRepository.save(event);
        }
        if (!events.isEmpty()) {
            log.info("Published {} outbox events", events.size());
        }
    }
}

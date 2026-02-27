package com.shopping.inventory.service;

import com.shopping.inventory.entity.IdempotencyRecord;
import com.shopping.inventory.entity.IdempotencyRecordId;
import com.shopping.inventory.repository.IdempotencyRecordRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyRecordRepository idempotencyRecordRepository;

    @Transactional(readOnly = true)
    public boolean isProcessed(String consumerId, UUID eventId) {
        return idempotencyRecordRepository.existsById(new IdempotencyRecordId(consumerId, eventId));
    }

    @Transactional
    public void markProcessed(String consumerId, UUID eventId) {
        idempotencyRecordRepository.save(IdempotencyRecord.builder()
                .id(new IdempotencyRecordId(consumerId, eventId))
                .processedAt(LocalDateTime.now())
                .build());
    }
}

package com.shopping.payment.service;

import com.shopping.payment.domain.IdempotencyRecord;
import com.shopping.payment.domain.IdempotencyRecordId;
import com.shopping.payment.repository.IdempotencyRecordRepository;
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
        IdempotencyRecord record = new IdempotencyRecord();
        record.setId(new IdempotencyRecordId(consumerId, eventId));
        idempotencyRecordRepository.save(record);
    }
}

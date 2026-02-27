package com.shopping.payment.repository;

import com.shopping.payment.domain.IdempotencyRecord;
import com.shopping.payment.domain.IdempotencyRecordId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, IdempotencyRecordId> {
}

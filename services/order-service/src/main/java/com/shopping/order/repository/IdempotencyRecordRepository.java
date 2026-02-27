package com.shopping.order.repository;

import com.shopping.order.entity.IdempotencyRecord;
import com.shopping.order.entity.IdempotencyRecordId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, IdempotencyRecordId> {
}

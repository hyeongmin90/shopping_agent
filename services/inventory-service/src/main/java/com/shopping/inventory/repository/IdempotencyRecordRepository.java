package com.shopping.inventory.repository;

import com.shopping.inventory.entity.IdempotencyRecord;
import com.shopping.inventory.entity.IdempotencyRecordId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, IdempotencyRecordId> {
}

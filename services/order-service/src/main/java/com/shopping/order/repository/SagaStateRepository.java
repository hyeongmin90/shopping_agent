package com.shopping.order.repository;

import com.shopping.order.entity.SagaState;
import com.shopping.order.enums.SagaStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SagaStateRepository extends JpaRepository<SagaState, UUID> {
    Optional<SagaState> findByOrderId(UUID orderId);

    List<SagaState> findByStatusAndTimeoutAtBefore(SagaStatus status, LocalDateTime timeoutAt);
}

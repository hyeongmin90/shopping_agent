package com.shopping.order.service;

import com.shopping.order.entity.OrderEntity;
import com.shopping.order.entity.SagaState;
import com.shopping.order.repository.OrderRepository;
import java.time.LocalDateTime;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class StuckSagaReaper {

    private final OrderService orderService;
    private final OrderRepository orderRepository;

    @Transactional
    @Scheduled(fixedDelayString = "${app.saga.reaper-interval-ms:10000}")
    public void reapTimedOutSagas() {
        LocalDateTime now = LocalDateTime.now();

        // 1. Handle RUNNING sagas that timed out — trigger compensation
        List<SagaState> timedOutRunning = orderService.findTimedOutSagas(now);
        for (SagaState sagaState : timedOutRunning) {
            try {
                OrderEntity order = orderRepository.findById(sagaState.getOrderId()).orElse(null);
                if (order == null) {
                    continue;
                }
                String reason = "Saga timeout while in step " + sagaState.getCurrentStep();
                orderService.createCompensationCommands(order, reason);
                orderService.markSagaCompensating(order.getId(), reason);
                log.warn("Compensation triggered for timed out order {}", order.getId());
            } catch (ObjectOptimisticLockingFailureException e) {
                log.warn("Optimistic lock conflict for timed out order {}, will retry next cycle: {}",
                        sagaState.getOrderId(), e.getMessage());
            }
        }

        // 2. Handle COMPENSATING sagas that timed out — retry compensation
        List<SagaState> stuckCompensating = orderService.findStuckCompensatingSagas(now);
        for (SagaState sagaState : stuckCompensating) {
            try {
                if ("COMPENSATION_DONE".equals(sagaState.getCurrentStep())
                        || "COMPENSATION_EXHAUSTED".equals(sagaState.getCurrentStep())) {
                    continue;
                }
                log.warn("Retrying stuck compensation for order {} (attempt {})",
                        sagaState.getOrderId(), sagaState.getRetryCount() + 1);
                orderService.retryCompensation(sagaState);
            } catch (ObjectOptimisticLockingFailureException e) {
                log.warn("Optimistic lock conflict for compensating order {}, will retry next cycle: {}",
                        sagaState.getOrderId(), e.getMessage());
            }
        }
    }
}

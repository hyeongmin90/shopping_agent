package com.shopping.order.service;

import com.shopping.order.entity.OrderEntity;
import com.shopping.order.entity.SagaState;
import com.shopping.order.repository.OrderRepository;
import java.time.LocalDateTime;
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
        List<SagaState> timedOut = orderService.findTimedOutSagas(LocalDateTime.now());
        for (SagaState sagaState : timedOut) {
            OrderEntity order = orderRepository.findById(sagaState.getOrderId()).orElse(null);
            if (order == null) {
                continue;
            }
            String reason = "Saga timeout while in step " + sagaState.getCurrentStep();
            orderService.createCompensationCommands(order, reason);
            orderService.markSagaCompensating(order.getId(), reason);
            log.warn("Compensation triggered for timed out order {}", order.getId());
        }
    }
}

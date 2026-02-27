package com.shopping.inventory.service;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationExpiryScheduler {

    private final InventoryCommandService inventoryCommandService;

    @Scheduled(fixedDelayString = "PT1M")
    public void expireReservations() {
        LocalDateTime now = LocalDateTime.now();
        inventoryCommandService.expireReservations(now);
        log.debug("Reservation expiry scan finished at {}", now);
    }
}

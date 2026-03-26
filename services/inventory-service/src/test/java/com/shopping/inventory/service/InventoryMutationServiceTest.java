package com.shopping.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shopping.inventory.entity.Inventory;
import com.shopping.inventory.entity.InventoryReservation;
import com.shopping.inventory.enums.ReservationStatus;
import com.shopping.inventory.exception.InsufficientStockException;
import com.shopping.inventory.kafka.model.ReserveInventoryCommand;
import com.shopping.inventory.kafka.model.ReserveInventoryItemCommand;
import com.shopping.inventory.repository.InventoryRepository;
import com.shopping.inventory.repository.InventoryReservationRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventoryMutationServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryReservationRepository reservationRepository;

    @InjectMocks
    private InventoryMutationService inventoryMutationService;

    private UUID productId;
    private UUID variantId;
    private Inventory inventory;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        variantId = UUID.randomUUID();
        inventory = Inventory.builder()
                .id(UUID.randomUUID())
                .productId(productId)
                .variantId(variantId)
                .sku("SKU-123")
                .totalQuantity(100)
                .reservedQuantity(0)
                .build();
    }

    @Test
    @DisplayName("재고 예약 성공")
    void reserve_Success() {
        // given
        ReserveInventoryItemCommand item = new ReserveInventoryItemCommand();
        item.setProductId(productId);
        item.setVariantId(variantId);
        item.setQuantity(10);

        ReserveInventoryCommand command = new ReserveInventoryCommand();
        command.setOrderId(UUID.randomUUID());
        command.setItems(List.of(item));

        when(inventoryRepository.findByProductIdAndVariantId(productId, variantId)).thenReturn(Optional.of(inventory));
        when(inventoryRepository.findById(inventory.getId())).thenReturn(Optional.of(inventory));
        when(inventoryRepository.saveAndFlush(any(Inventory.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reservationRepository.save(any(InventoryReservation.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        InventoryReservation reservation = inventoryMutationService.reserve(command, 10);

        // then
        assertThat(reservation.getOrderId()).isEqualTo(command.getOrderId());
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(inventory.getReservedQuantity()).isEqualTo(10);
        verify(inventoryRepository, atLeastOnce()).saveAndFlush(any());
    }

    @Test
    @DisplayName("재고 예약 실패 - 재고 부족")
    void reserve_InsufficientStock() {
        // given
        ReserveInventoryItemCommand item = new ReserveInventoryItemCommand();
        item.setProductId(productId);
        item.setVariantId(variantId);
        item.setQuantity(150); // total is 100

        ReserveInventoryCommand command = new ReserveInventoryCommand();
        command.setOrderId(UUID.randomUUID());
        command.setItems(List.of(item));

        when(inventoryRepository.findByProductIdAndVariantId(productId, variantId)).thenReturn(Optional.of(inventory));
        when(inventoryRepository.findById(inventory.getId())).thenReturn(Optional.of(inventory));

        // when & then
        assertThatThrownBy(() -> inventoryMutationService.reserve(command, 10))
                .isInstanceOf(InsufficientStockException.class);
    }
}

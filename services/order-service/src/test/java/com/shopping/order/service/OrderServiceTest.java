package com.shopping.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shopping.order.dto.ApproveOrderRequest;
import com.shopping.order.dto.OrderResponse;
import com.shopping.order.dto.OrderStatusResponse;
import com.shopping.order.entity.OrderEntity;
import com.shopping.order.entity.SagaState;
import com.shopping.order.enums.OrderSagaStatus;
import com.shopping.order.enums.OrderStatus;
import com.shopping.order.exception.BadRequestException;
import com.shopping.order.exception.NotFoundException;
import com.shopping.order.repository.OrderRepository;
import com.shopping.order.repository.SagaStateRepository;
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
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private SagaStateRepository sagaStateRepository;

    @Mock
    private OutboxService outboxService;

    @InjectMocks
    private OrderService orderService;

    private UUID orderId;
    private OrderEntity order;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        order = new OrderEntity();
        order.setId(orderId);
        order.setUserId("test-user-id");
        order.setStatus(OrderStatus.PENDING_APPROVAL);
        order.setSagaStatus(OrderSagaStatus.NONE);
        order.setTotalAmount(10000);
    }

    @Test
    @DisplayName("주문 조회 성공")
    void getOrder_Success() {
        // given
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        // when
        OrderResponse response = orderService.getOrder(orderId);

        // then
        assertThat(response.getId()).isEqualTo(orderId);
        verify(orderRepository).findById(orderId);
    }

    @Test
    @DisplayName("주문 조회 실패 - 존재하지 않는 주문")
    void getOrder_NotFound() {
        // given
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> orderService.getOrder(orderId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("주문 승인 성공 - Saga 시작")
    void approve_Success() {
        // given
        ApproveOrderRequest request = new ApproveOrderRequest();
        request.setPaymentMethod("CARD");

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(sagaStateRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

        // when
        OrderStatusResponse response = orderService.approve(orderId, request);

        // then
        assertThat(response.getStatus()).isEqualTo(OrderStatus.INVENTORY_RESERVING);
        verify(orderRepository).save(order);
        verify(sagaStateRepository).save(any(SagaState.class));
        verify(outboxService).enqueue(
                eq("ORDER"),
                eq(orderId),
                eq("ReserveInventoryCommand"),
                any(),
                eq(orderId),
                any(),
                anyString());
    }

    @Test
    @DisplayName("주문 승인 실패 - 잘못된 상태")
    void approve_InvalidStatus() {
        // given
        order.setStatus(OrderStatus.CONFIRMED);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> orderService.approve(orderId, new ApproveOrderRequest()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("주문 취소 성공")
    void cancel_Success() {
        // given
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(sagaStateRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

        // when
        OrderStatusResponse response = orderService.cancel(orderId, "user cancel");

        // then
        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderRepository).save(order);
        verify(outboxService).enqueue(
                eq("ORDER"),
                eq(orderId),
                eq("OrderCancelled"),
                any(),
                eq(orderId),
                any(),
                anyString());
    }
}

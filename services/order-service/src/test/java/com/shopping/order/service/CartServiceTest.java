package com.shopping.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shopping.order.client.ProductDto;
import com.shopping.order.client.ProductServiceClient;
import com.shopping.order.dto.CartItemRequest;
import com.shopping.order.dto.CartResponse;
import com.shopping.order.dto.CheckoutRequest;
import com.shopping.order.dto.CheckoutResponse;
import com.shopping.order.entity.CartEntity;
import com.shopping.order.entity.CartItemEntity;
import com.shopping.order.entity.OrderEntity;
import com.shopping.order.exception.BadRequestException;
import com.shopping.order.repository.CartItemRepository;
import com.shopping.order.repository.CartRepository;
import com.shopping.order.repository.OrderRepository;
import java.util.ArrayList;
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
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductServiceClient productServiceClient;

    @InjectMocks
    private CartService cartService;

    private String userId;
    private CartEntity cart;

    @BeforeEach
    void setUp() {
        userId = "test-user-id";
        cart = new CartEntity();
        cart.setId(UUID.randomUUID());
        cart.setUserId(userId);
        cart.setItems(new ArrayList<>());
        cart.setTotalAmount(0);
        cart.setCurrency("KRW");
    }

    @Test
    @DisplayName("장바구니 조회 - 없으면 생성")
    void getCart_CreateIfNotFound() {
        // given
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(cartRepository.save(any(CartEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        CartResponse response = cartService.getCart(userId);

        // then
        assertThat(response.getUserId()).isEqualTo(userId);
        verify(cartRepository).findByUserId(userId);
        verify(cartRepository).save(any(CartEntity.class));
    }

    @Test
    @DisplayName("장바구니 아이템 추가")
    void addItem_Success() {
        // given
        CartItemRequest request = new CartItemRequest();
        request.setProductId(UUID.randomUUID());
        request.setQuantity(2);

        ProductDto productDto = new ProductDto();
        productDto.setId(request.getProductId());
        productDto.setName("Test Product");
        productDto.setBasePrice(1000);

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(productServiceClient.getProduct(request.getProductId())).thenReturn(productDto);
        when(cartItemRepository.save(any(CartItemEntity.class))).thenAnswer(inv -> {
            CartItemEntity item = inv.getArgument(0);
            item.setId(UUID.randomUUID());
            return item;
        });

        // when
        CartResponse response = cartService.addItem(userId, request);

        // then
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getTotalAmount()).isEqualTo(2000);
        verify(cartRepository).save(cart);
    }

    @Test
    @DisplayName("체크아웃 성공 - 주문 생성 및 장바구니 비우기")
    void checkout_Success() {
        // given
        CartItemEntity item = new CartItemEntity();
        item.setId(UUID.randomUUID());
        item.setProductId(UUID.randomUUID());
        item.setQuantity(2);
        cart.getItems().add(item);
        cart.setTotalAmount(2000);

        CheckoutRequest request = new CheckoutRequest();
        request.setShippingAddress("Seoul, Korea");

        ProductDto productDto = new ProductDto();
        productDto.setName("Test Product");
        productDto.setBasePrice(1000);

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(productServiceClient.getProduct(any())).thenReturn(productDto);
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(inv -> {
            OrderEntity order = inv.getArgument(0);
            order.setId(UUID.randomUUID());
            return order;
        });

        // when
        CheckoutResponse response = cartService.checkout(userId, request);

        // then
        assertThat(response.getTotalAmount()).isEqualTo(2000);
        assertThat(cart.getItems()).isEmpty();
        assertThat(cart.getTotalAmount()).isZero();
        verify(orderRepository).save(any(OrderEntity.class));
        verify(cartRepository).save(cart);
    }

    @Test
    @DisplayName("체크아웃 실패 - 빈 장바구니")
    void checkout_EmptyCart() {
        // given
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));

        // when & then
        assertThatThrownBy(() -> cartService.checkout(userId, new CheckoutRequest()))
                .isInstanceOf(BadRequestException.class);
    }
}

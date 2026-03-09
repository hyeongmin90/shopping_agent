package com.shopping.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shopping.product.domain.Category;
import com.shopping.product.domain.Product;
import com.shopping.product.dto.ProductResponse;
import com.shopping.product.dto.ProductSearchRequest;
import com.shopping.product.exception.ResourceNotFoundException;
import com.shopping.product.repository.CategoryRepository;
import com.shopping.product.repository.ProductRepository;
import com.shopping.product.repository.ProductVariantRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    @DisplayName("상품 상세 조회 성공")
    void getProduct_Success() {
        // given
        UUID productId = UUID.randomUUID();
        Product product = new Product();
        product.setId(productId);
        product.setName("Test Product");
        product.setBasePrice(1000);
        product.setVariants(List.of());

        when(productRepository.findDetailedById(productId)).thenReturn(Optional.of(product));

        // when
        ProductResponse response = productService.getProduct(productId);

        // then
        assertThat(response.id()).isEqualTo(productId);
        assertThat(response.name()).isEqualTo("Test Product");
        verify(productRepository).findDetailedById(productId);
    }

    @Test
    @DisplayName("상품 상세 조회 실패 - 존재하지 않음")
    void getProduct_NotFound() {
        // given
        UUID productId = UUID.randomUUID();
        when(productRepository.findDetailedById(productId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> productService.getProduct(productId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("상품 검색 성공")
    void searchProducts_Success() {
        // given
        ProductSearchRequest request = ProductSearchRequest.builder()
                .keyword("phone")
                .build();
        Pageable pageable = PageRequest.of(0, 10);
        
        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setName("iPhone");
        
        Page<Product> page = new PageImpl<>(List.of(product), pageable, 1);

        when(productRepository.searchProducts(anyBoolean(), any(), any(), any(), any(), eq("phone"), eq(pageable)))
                .thenReturn(page);

        // when
        Page<ProductResponse> result = productService.searchProducts(request, pageable);

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("iPhone");
    }

    @Test
    @DisplayName("카테고리 트리 조회 성공")
    void getCategoryTree_Success() {
        // given
        Category root = new Category();
        root.setId(UUID.randomUUID());
        root.setName("Electronics");

        Category child = new Category();
        child.setId(UUID.randomUUID());
        child.setName("Phones");
        child.setParent(root);

        when(categoryRepository.findAllByOrderByNameAsc()).thenReturn(List.of(root, child));

        // when
        var tree = productService.getCategoryTree();

        // then
        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).name()).isEqualTo("Electronics");
        assertThat(tree.get(0).children()).hasSize(1);
        assertThat(tree.get(0).children().get(0).name()).isEqualTo("Phones");
    }
}

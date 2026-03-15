package com.shopping.product.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shopping.product.config.CorrelationIdFilter;
import com.shopping.product.dto.ProductRequest;
import com.shopping.product.dto.ProductResponse;
import com.shopping.product.dto.ProductSearchRequest;
import com.shopping.product.kafka.ProductEventPublisher;
import com.shopping.product.service.ProductService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ProductController.class, excludeFilters = {
    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = CorrelationIdFilter.class)
})
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;

    @MockBean
    private ProductEventPublisher productEventPublisher;

    @Test
    @DisplayName("상품 검색 API")
    void getProducts_Api() throws Exception {
        // given
        ProductResponse product = ProductResponse.builder()
                .id(UUID.randomUUID())
                .name("iPhone")
                .build();
        
        when(productService.searchProducts(any(ProductSearchRequest.class), any()))
                .thenReturn(new PageImpl<>(List.of(product)));

        // when & then
        mockMvc.perform(get("/api/products")
                .param("search", "iPhone"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("iPhone"));
    }

    @Test
    @DisplayName("상품 상세 조회 API")
    void getProduct_Api() throws Exception {
        // given
        UUID productId = UUID.randomUUID();
        ProductResponse product = ProductResponse.builder()
                .id(productId)
                .name("iPhone")
                .build();

        when(productService.getProduct(productId)).thenReturn(product);

        // when & then
        mockMvc.perform(get("/api/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(productId.toString()))
                .andExpect(jsonPath("$.name").value("iPhone"));
    }

    @Test
    @DisplayName("카테고리 트리 조회 API")
    void getCategories_Api() throws Exception {
        // given
        when(productService.getCategoryTree()).thenReturn(List.of());

        // when & then
        mockMvc.perform(get("/api/products/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("상품 등록 API - 성공")
    void addProduct_Api_Success() throws Exception {
        // given
        UUID productId = UUID.randomUUID();
        ProductResponse product = ProductResponse.builder()
                .id(productId)
                .name("New Product")
                .basePrice(15000)
                .currency("KRW")
                .status("ACTIVE")
                .build();

        when(productService.addProduct(any(ProductRequest.class))).thenReturn(product);

        String requestBody = """
                {
                    "name": "New Product",
                    "basePrice": 15000
                }
                """;

        // when & then
        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(productId.toString()))
                .andExpect(jsonPath("$.name").value("New Product"))
                .andExpect(jsonPath("$.basePrice").value(15000));
    }

    @Test
    @DisplayName("상품 등록 API - 이름 누락 시 400 에러")
    void addProduct_Api_MissingName() throws Exception {
        String requestBody = """
                {
                    "basePrice": 15000
                }
                """;

        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest());
    }
}

package com.shopping.product.controller;

import com.shopping.product.dto.CategoryResponse;
import com.shopping.product.dto.ProductResponse;
import com.shopping.product.dto.ProductSearchRequest;
import com.shopping.product.dto.ProductVariantResponse;
import com.shopping.product.kafka.ProductEventPublisher;
import com.shopping.product.service.ProductService;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductEventPublisher productEventPublisher;

    @GetMapping("/products")
    public Page<ProductResponse> getProducts(
        @RequestParam(required = false) UUID category,
        @RequestParam(required = false) String brand,
        @RequestParam(required = false) @Min(0) Integer minPrice,
        @RequestParam(required = false) @Min(0) Integer maxPrice,
        @RequestParam(required = false) String search,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) int size,
        @RequestParam(defaultValue = "createdAt,desc") String[] sort
    ) {
        ProductSearchRequest request = ProductSearchRequest.builder()
            .categoryId(category)
            .brand(brand)
            .minPrice(minPrice)
            .maxPrice(maxPrice)
            .keyword(search)
            .build();
        return productService.searchProducts(request, buildPageable(page, size, sort));
    }

    @GetMapping("/products/{id}")
    public ProductResponse getProduct(@PathVariable UUID id) {
        ProductResponse product = productService.getProduct(id);
        productEventPublisher.publishProductViewed(product);
        return product;
    }

    @GetMapping("/products/{id}/variants")
    public List<ProductVariantResponse> getProductVariants(@PathVariable UUID id) {
        return productService.getVariants(id);
    }

    @GetMapping("/categories")
    public List<CategoryResponse> getCategories() {
        return productService.getCategoryTree();
    }

    private Pageable buildPageable(int page, int size, String[] sort) {
        if (sort.length == 0 || sort[0].isBlank()) {
            return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        }

        String sortField = sort[0];
        Sort.Direction direction = Sort.Direction.DESC;
        if (sort.length > 1) {
            direction = Sort.Direction.fromOptionalString(sort[1]).orElse(Sort.Direction.DESC);
        } else if (sort[0].contains(",")) {
            String[] split = sort[0].split(",");
            sortField = split[0];
            if (split.length > 1) {
                direction = Sort.Direction.fromOptionalString(split[1]).orElse(Sort.Direction.DESC);
            }
        }

        return PageRequest.of(page, size, Sort.by(direction, sortField));
    }
}

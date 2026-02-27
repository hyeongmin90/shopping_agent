package com.shopping.product.service;

import com.shopping.product.domain.Category;
import com.shopping.product.domain.Product;
import com.shopping.product.domain.ProductVariant;
import com.shopping.product.dto.CategoryResponse;
import com.shopping.product.dto.ProductResponse;
import com.shopping.product.dto.ProductSearchRequest;
import com.shopping.product.dto.ProductVariantResponse;
import com.shopping.product.exception.ResourceNotFoundException;
import com.shopping.product.repository.CategoryRepository;
import com.shopping.product.repository.ProductRepository;
import com.shopping.product.repository.ProductVariantRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final CategoryRepository categoryRepository;

    @Cacheable(value = "productSearch", key = "#request.cacheKey() + ':' + #pageable.pageNumber + ':' + #pageable.pageSize + ':' + #pageable.sort.toString()")
    public Page<ProductResponse> searchProducts(ProductSearchRequest request, Pageable pageable) {
        String keyword = normalize(request.keyword());
        String brand = normalize(request.brand());

        Page<Product> products = keyword.isBlank()
            ? productRepository.searchProducts(request.categoryId(), brandOrNull(brand), request.minPrice(), request.maxPrice(), null, pageable)
            : productRepository.fullTextSearch(
                keyword,
                request.categoryId() == null ? null : request.categoryId().toString(),
                brandOrNull(brand),
                request.minPrice(),
                request.maxPrice(),
                pageable
            );

        return products.map(product -> toProductResponse(product, null));
    }

    @Cacheable(value = "productDetails", key = "#productId")
    public ProductResponse getProduct(UUID productId) {
        Product product = productRepository.findDetailedById(productId)
            .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
        return toProductResponse(product, product.getVariants());
    }

    public List<ProductVariantResponse> getVariants(UUID productId) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product not found: " + productId);
        }
        return productVariantRepository.findByProductIdOrderByNameAsc(productId)
            .stream()
            .map(this::toVariantResponse)
            .toList();
    }

    @Cacheable(value = "categoryTree", key = "'root'")
    public List<CategoryResponse> getCategoryTree() {
        List<Category> categories = categoryRepository.findAllByOrderByNameAsc();
        Map<UUID, CategoryResponse> map = new HashMap<>();
        List<CategoryResponse> roots = new ArrayList<>();

        for (Category category : categories) {
            map.put(category.getId(), CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .parentId(category.getParent() == null ? null : category.getParent().getId())
                .description(category.getDescription())
                .children(new ArrayList<>())
                .build());
        }

        for (Category category : categories) {
            CategoryResponse current = map.get(category.getId());
            if (category.getParent() == null) {
                roots.add(current);
                continue;
            }
            CategoryResponse parent = map.get(category.getParent().getId());
            if (parent != null) {
                parent.children().add(current);
            } else {
                roots.add(current);
            }
        }

        return roots;
    }

    private ProductResponse toProductResponse(Product product, List<ProductVariant> variants) {
        List<ProductVariantResponse> variantResponses = variants == null
            ? List.of()
            : variants.stream().map(this::toVariantResponse).toList();

        return ProductResponse.builder()
            .id(product.getId())
            .name(product.getName())
            .description(product.getDescription())
            .brand(product.getBrand())
            .categoryId(product.getCategory() == null ? null : product.getCategory().getId())
            .categoryName(product.getCategory() == null ? null : product.getCategory().getName())
            .basePrice(product.getBasePrice())
            .currency(product.getCurrency())
            .imageUrl(product.getImageUrl())
            .status(product.getStatus())
            .shippingDays(product.getShippingDays())
            .compatibilityTags(product.getCompatibilityTags())
            .createdAt(product.getCreatedAt())
            .updatedAt(product.getUpdatedAt())
            .variants(variantResponses)
            .build();
    }

    private ProductVariantResponse toVariantResponse(ProductVariant variant) {
        return ProductVariantResponse.builder()
            .id(variant.getId())
            .sku(variant.getSku())
            .name(variant.getName())
            .size(variant.getSize())
            .color(variant.getColor())
            .priceAdjustment(variant.getPriceAdjustment())
            .status(variant.getStatus())
            .attributes(variant.getAttributes())
            .build();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String brandOrNull(String brand) {
        return brand.isBlank() ? null : brand;
    }
}

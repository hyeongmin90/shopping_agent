package com.shopping.product.service;

import com.shopping.product.domain.Category;
import com.shopping.product.domain.Product;
import com.shopping.product.domain.ProductVariant;
import com.shopping.product.dto.AddVariantsRequest;
import com.shopping.product.dto.CategoryResponse;
import com.shopping.product.dto.ProductRequest;
import com.shopping.product.dto.ProductResponse;
import com.shopping.product.dto.ProductSearchRequest;
import com.shopping.product.dto.ProductVariantResponse;
import com.shopping.product.exception.ResourceNotFoundException;
import com.shopping.product.kafka.ProductEventPublisher;
import com.shopping.product.repository.CategoryRepository;
import com.shopping.product.repository.ProductRepository;
import com.shopping.product.repository.ProductVariantRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
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
    private final ProductEventPublisher productEventPublisher;

    @Transactional
    @CacheEvict(value = {"productSearch", "categoryTree"}, allEntries = true)
    public ProductResponse addProduct(ProductRequest request) {
        Category category = null;
        if (request.getCategoryId() != null) {
            category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + request.getCategoryId()));
        }

        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setBrand(request.getBrand());
        product.setCategory(category);
        product.setBasePrice(request.getBasePrice());
        product.setCurrency(request.getCurrency() != null ? request.getCurrency() : "KRW");
        product.setImageUrl(request.getImageUrl());
        product.setStatus("ACTIVE");
        product.setShippingDays(request.getShippingDays());
        product.setCompatibilityTags(request.getCompatibilityTags() != null ? request.getCompatibilityTags() : List.of());

        Product savedProduct = productRepository.save(product);

        List<ProductVariant> savedVariants = new ArrayList<>();
        if (request.getVariants() != null && !request.getVariants().isEmpty()) {
            for (ProductRequest.VariantRequest vr : request.getVariants()) {
                ProductVariant variant = new ProductVariant();
                variant.setId(UUID.randomUUID());
                variant.setProduct(savedProduct);
                variant.setSku(vr.sku());
                variant.setName(vr.name());
                variant.setSize(vr.size());
                variant.setColor(vr.color());
                variant.setPriceAdjustment(vr.priceAdjustment() != null ? vr.priceAdjustment() : 0);
                variant.setAttributes(vr.attributes());
                variant.setStatus("ACTIVE");
                savedVariants.add(productVariantRepository.save(variant));
            }
        }

        ProductResponse response = toProductResponse(savedProduct, savedVariants);
        productEventPublisher.publishProductIndexEvent(response, "ProductCreatedEvent");
        return response;
    }

    @Cacheable(value = "productSearch", key = "T(java.lang.String).format('%s:%d:%d:%s', #request.cacheKey(), #pageable.pageNumber, #pageable.pageSize, #pageable.sort.toString())")
    public Page<ProductResponse> searchProducts(ProductSearchRequest request, Pageable pageable) {
        String keyword = normalize(request.keyword());
        String brand = normalize(request.brand());

        List<UUID> categoryIds = null;
        if (request.category() != null && !request.category().isBlank()) {
            UUID rootCategoryId;
            try {
                rootCategoryId = UUID.fromString(request.category().trim());
            } catch (IllegalArgumentException e) {
                rootCategoryId = categoryRepository.findByName(request.category().trim())
                        .map(Category::getId)
                        .orElse(null);
            }
            if (rootCategoryId == null) {
                return Page.empty(pageable);
            }
            categoryIds = gatherCategoryIds(rootCategoryId);
        }

        boolean filterCategory = categoryIds != null && !categoryIds.isEmpty();
        List<UUID> safeCategoryIds = filterCategory ? categoryIds : List.of();

        Page<Product> products = productRepository.searchProducts(
                filterCategory,
                safeCategoryIds,
                brandOrNull(brand),
                request.minPrice(),
                request.maxPrice(),
                keyword,
                pageable);

        return products.map(product -> toProductResponse(product, null));
    }

    private List<UUID> gatherCategoryIds(UUID rootCategoryId) {
        List<UUID> ids = new ArrayList<>();
        ids.add(rootCategoryId);

        // Find the root node in the tree
        CategoryResponse rootNode = findCategoryInTree(getCategoryTree(), rootCategoryId);
        if (rootNode != null) {
            collectAllChildIds(rootNode, ids);
        }
        return ids;
    }

    private CategoryResponse findCategoryInTree(List<CategoryResponse> tree, UUID targetId) {
        if (tree == null)
            return null;
        for (CategoryResponse node : tree) {
            if (node.id().equals(targetId)) {
                return node;
            }
            CategoryResponse found = findCategoryInTree(node.children(), targetId);
            if (found != null)
                return found;
        }
        return null;
    }

    private void collectAllChildIds(CategoryResponse node, List<UUID> ids) {
        if (node.children() == null)
            return;
        for (CategoryResponse child : node.children()) {
            ids.add(child.id());
            collectAllChildIds(child, ids);
        }
    }

    @Cacheable(value = "productDetails", key = "#productId")
    public ProductResponse getProduct(UUID productId) {
        Product product = productRepository.findDetailedById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
        return toProductResponse(product, product.getVariants());
    }

    @Transactional
    @CacheEvict(value = "productDetails", key = "#productId")
    public List<ProductVariantResponse> addVariants(UUID productId, AddVariantsRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));

        List<ProductVariant> saved = new ArrayList<>();
        for (AddVariantsRequest.VariantRequest vr : request.variants()) {
            ProductVariant variant = new ProductVariant();
            variant.setId(UUID.randomUUID());
            variant.setProduct(product);
            variant.setSku(vr.sku());
            variant.setName(vr.name());
            variant.setSize(vr.size());
            variant.setColor(vr.color());
            variant.setPriceAdjustment(vr.priceAdjustment() != null ? vr.priceAdjustment() : 0);
            variant.setAttributes(vr.attributes());
            variant.setStatus("ACTIVE");
            saved.add(productVariantRepository.save(variant));
        }

        return saved.stream().map(this::toVariantResponse).toList();
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

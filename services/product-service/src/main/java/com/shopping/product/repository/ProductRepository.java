package com.shopping.product.repository;

import com.shopping.product.domain.Product;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    @EntityGraph(attributePaths = {"category", "variants"})
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findDetailedById(@Param("id") UUID id);

    @Query(value = """
        SELECT p.* FROM product p
        WHERE (:filterCategory = false OR p.category_id IN :categoryIds)
          AND (:brand IS NULL OR LOWER(p.brand) = LOWER(CAST(:brand AS text)))
          AND (:minPrice IS NULL OR p.base_price >= :minPrice)
          AND (:maxPrice IS NULL OR p.base_price <= :maxPrice)
          AND (
            :keyword IS NULL OR CAST(:keyword AS text) = ''
            OR to_tsvector('simple', p.name || ' ' || coalesce(p.description, '') || ' ' || coalesce(p.brand, '')) 
               @@ websearch_to_tsquery('simple', CAST(:keyword AS text))
          )
        """, 
        countQuery = """
        SELECT count(*) FROM product p
        WHERE (:filterCategory = false OR p.category_id IN :categoryIds)
          AND (:brand IS NULL OR LOWER(p.brand) = LOWER(CAST(:brand AS text)))
          AND (:minPrice IS NULL OR p.base_price >= :minPrice)
          AND (:maxPrice IS NULL OR p.base_price <= :maxPrice)
          AND (
            :keyword IS NULL OR CAST(:keyword AS text) = ''
            OR to_tsvector('simple', p.name || ' ' || coalesce(p.description, '') || ' ' || coalesce(p.brand, '')) 
               @@ websearch_to_tsquery('simple', CAST(:keyword AS text))
          )
        """,
        nativeQuery = true)
    Page<Product> searchProducts(
        @Param("filterCategory") boolean filterCategory,
        @Param("categoryIds") java.util.Collection<UUID> categoryIds,
        @Param("brand") String brand,
        @Param("minPrice") Integer minPrice,
        @Param("maxPrice") Integer maxPrice,
        @Param("keyword") String keyword,
        Pageable pageable
    );


}

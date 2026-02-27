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

    @EntityGraph(attributePaths = {"category"})
    @Query("""
        select p from Product p
        where (:categoryId is null or p.category.id = :categoryId)
          and (:brand is null or lower(p.brand) = lower(:brand))
          and (:minPrice is null or p.basePrice >= :minPrice)
          and (:maxPrice is null or p.basePrice <= :maxPrice)
          and (
            :keyword is null or :keyword = ''
            or lower(p.name) like lower(concat('%', :keyword, '%'))
            or lower(coalesce(p.description, '')) like lower(concat('%', :keyword, '%'))
          )
        """)
    Page<Product> searchProducts(
        @Param("categoryId") UUID categoryId,
        @Param("brand") String brand,
        @Param("minPrice") Integer minPrice,
        @Param("maxPrice") Integer maxPrice,
        @Param("keyword") String keyword,
        Pageable pageable
    );

    @Query(
        value = """
            select p.* from products p
            where (:categoryId is null or p.category_id = cast(:categoryId as uuid))
              and (:brand is null or lower(p.brand) = lower(:brand))
              and (:minPrice is null or p.base_price >= :minPrice)
              and (:maxPrice is null or p.base_price <= :maxPrice)
              and to_tsvector('simple', p.name || ' ' || coalesce(p.description, ''))
                  @@ plainto_tsquery('simple', :keyword)
            """,
        countQuery = """
            select count(*) from products p
            where (:categoryId is null or p.category_id = cast(:categoryId as uuid))
              and (:brand is null or lower(p.brand) = lower(:brand))
              and (:minPrice is null or p.base_price >= :minPrice)
              and (:maxPrice is null or p.base_price <= :maxPrice)
              and to_tsvector('simple', p.name || ' ' || coalesce(p.description, ''))
                  @@ plainto_tsquery('simple', :keyword)
            """,
        nativeQuery = true
    )
    Page<Product> fullTextSearch(
        @Param("keyword") String keyword,
        @Param("categoryId") String categoryId,
        @Param("brand") String brand,
        @Param("minPrice") Integer minPrice,
        @Param("maxPrice") Integer maxPrice,
        Pageable pageable
    );
}

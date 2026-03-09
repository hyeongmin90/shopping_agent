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
        where (:filterCategory = false or p.category.id in :categoryIds)
          and (:brand is null or lower(cast(:brand as string)) = lower(p.brand))
          and (:minPrice is null or p.basePrice >= :minPrice)
          and (:maxPrice is null or p.basePrice <= :maxPrice)
          and (
            :keyword is null or cast(:keyword as string) = ''
            or lower(p.name) like lower(concat('%', cast(:keyword as string), '%'))
          )
        """)
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

package com.shopping.review.repository;

import com.shopping.review.domain.Review;
import com.shopping.review.domain.SizeFeedback;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    @Query("""
            SELECT r
            FROM Review r
            WHERE r.productId = :productId
              AND (:minRating IS NULL OR r.rating >= :minRating)
              AND (:maxRating IS NULL OR r.rating <= :maxRating)
              AND (:sizeFeedback IS NULL OR r.sizeFeedback = :sizeFeedback)
              AND (:verifiedOnly = false OR r.verifiedPurchase = true)
            """)
    Page<Review> findByProductWithFilters(
            @Param("productId") UUID productId,
            @Param("minRating") Integer minRating,
            @Param("maxRating") Integer maxRating,
            @Param("sizeFeedback") SizeFeedback sizeFeedback,
            @Param("verifiedOnly") boolean verifiedOnly,
            Pageable pageable
    );

    @Query("""
            SELECT AVG(r.rating)
            FROM Review r
            WHERE r.productId = :productId
            """)
    Double findAverageRatingByProductId(@Param("productId") UUID productId);

    @Query("""
            SELECT AVG(r.qualityRating)
            FROM Review r
            WHERE r.productId = :productId
            """)
    Double findAverageQualityRatingByProductId(@Param("productId") UUID productId);

    @Query("""
            SELECT r.rating AS rating, COUNT(r) AS count
            FROM Review r
            WHERE r.productId = :productId
            GROUP BY r.rating
            """)
    List<RatingCountView> countByRatingForProduct(@Param("productId") UUID productId);

    @Query("""
            SELECT r.sizeFeedback AS sizeFeedback, COUNT(r) AS count
            FROM Review r
            WHERE r.productId = :productId
              AND r.sizeFeedback IS NOT NULL
            GROUP BY r.sizeFeedback
            """)
    List<SizeFeedbackCountView> countBySizeFeedbackForProduct(@Param("productId") UUID productId);

    @Query(value = """
            SELECT *
            FROM reviews r
            WHERE r.product_id = :productId
              AND to_tsvector('simple', COALESCE(r.title, '') || ' ' || COALESCE(r.content, ''))
                  @@ plainto_tsquery('simple', :keyword)
              AND (:minRating IS NULL OR r.rating >= :minRating)
              AND (:maxRating IS NULL OR r.rating <= :maxRating)
              AND (:verifiedOnly = false OR r.verified_purchase = true)
            """, countQuery = """
            SELECT COUNT(*)
            FROM reviews r
            WHERE r.product_id = :productId
              AND to_tsvector('simple', COALESCE(r.title, '') || ' ' || COALESCE(r.content, ''))
                  @@ plainto_tsquery('simple', :keyword)
              AND (:minRating IS NULL OR r.rating >= :minRating)
              AND (:maxRating IS NULL OR r.rating <= :maxRating)
              AND (:verifiedOnly = false OR r.verified_purchase = true)
            """, nativeQuery = true)
    Page<Review> searchByKeywordWithinProduct(
            @Param("productId") UUID productId,
            @Param("keyword") String keyword,
            @Param("minRating") Integer minRating,
            @Param("maxRating") Integer maxRating,
            @Param("verifiedOnly") boolean verifiedOnly,
            Pageable pageable
    );

    long countByProductId(UUID productId);
}

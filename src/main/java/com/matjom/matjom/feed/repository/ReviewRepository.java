package com.matjom.matjom.feed.repository;

import com.matjom.matjom.feed.entity.review.Review;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {

    Optional<Review> findByVisitId(Long visitId); // 9월 26일 최종: 중복 체크만 유지

    boolean existsByVisitId(Long visitId); // 9월 26일 최종

    List<Review> findByUserIdOrderByCreatedAtDesc(UUID userId); // 9월 26일 최종

    @Query("""
        SELECT r FROM Review r
        WHERE r.placeId = :placeId
          AND r.status = 'ACTIVE'
          AND r.deletedAt IS NULL
        ORDER BY r.createdAt DESC
    """)
    List<Review> findActiveReviewsByPlaceId(@Param("placeId") Long placeId); // 9월 26일 최종

    @Query("""
        SELECT COUNT(r) FROM Review r
        WHERE r.placeId = :placeId
          AND r.status = 'ACTIVE'
          AND r.deletedAt IS NULL
    """)
    long countActiveReviewsByPlaceId(@Param("placeId") Long placeId); // 9월 26일 최종

    @Query("""
        SELECT r FROM Review r
        WHERE r.userId = :userId
          AND r.placeId = :placeId
          AND r.deletedAt IS NULL
        ORDER BY r.createdAt DESC
    """)
    List<Review> findByUserIdAndPlaceId(@Param("userId") UUID userId,
                                        @Param("placeId") Long placeId); // 9월 26일 최종
}

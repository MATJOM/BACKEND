package com.matjom.matjom.feed.repository;

import com.matjom.matjom.feed.entity.review.Review;
import com.matjom.matjom.feed.entity.review.ReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<Review,UUID> {
    // 방문 기반 조회 (중복 체크용)
    Optional<Review> findByVisitId(Long visitId);
    boolean existsByVisitId(Long visitId);

    // 사용자별 조회 (프로필용)
    List<Review> findByUserIdOrderByCreatedAtDesc(UUID userId);

    // 장소별 조회 (활성 리뷰만) - 9월 24일 수정: BaseEntity의 deletedAt 필드 사용
    @Query("SELECT r FROM Review r WHERE r.placeId = :placeId AND r.status = 'ACTIVE' AND r.deletedAt IS NULL ORDER BY r.createdAt DESC")
    List<Review> findActiveReviewsByPlaceId(@Param("placeId") Long placeId);


    // 장소별 리뷰 수 (통계용) - 9월 24일 수정: BaseEntity의 deletedAt 필드 사용
    @Query("SELECT COUNT(r) FROM Review r WHERE r.placeId = :placeId AND r.status = 'ACTIVE' AND r.deletedAt IS NULL")
    long countActiveReviewsByPlaceId(@Param("placeId") Long placeId);

    // 신고된 리뷰 조회
    List<Review> findByFlaggedTrueAndStatusOrderByCreatedAtDesc(ReviewStatus status);

    // 사용자가 특정 장소에 작성한 리뷰들 (추천 알고리즘용) - 9월 24일 수정: BaseEntity의 deletedAt 필드 사용
    @Query("SELECT r FROM Review r WHERE r.userId = :userId AND r.placeId = :placeId AND r.deletedAt IS NULL ORDER BY r.createdAt DESC")
    List<Review> findByUserIdAndPlaceId(@Param("userId") UUID userId, @Param("placeId") Long placeId);
}

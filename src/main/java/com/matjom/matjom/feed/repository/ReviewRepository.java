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

    // 목적: 특정 방문에 이미 작성된 리뷰가 있는지 확인한다
    // 필요 이유: 방문당 1회 작성 제한을 지키기 위해서다
    // 로직: visitId로 Review를 검색해 Optional로 반환한다
    Optional<Review> findByVisitId(Long visitId); // 9월 26일 최종: 중복 체크만 유지

    // 목적: 리뷰 존재 여부를 빠르게 판별한다
    // 필요 이유: 자격 검증 단계에서 중복 저장을 차단한다
    // 로직: visitId 조건으로 COUNT 대신 exists 쿼리를 실행한다
    boolean existsByVisitId(Long visitId); // 9월 26일 최종

    // 목적: 사용자가 작성한 리뷰를 최신순으로 가져온다
    // 필요 이유: 마이페이지에서 최근 활동을 상단에 노출하기 위함이다
    // 로직: userId 기준으로 createdAt 내림차순 정렬한 리스트를 반환한다
    List<Review> findByUserIdOrderByCreatedAtDesc(UUID userId); // 9월 26일 최종

    @Query("""
        SELECT r FROM Review r
        WHERE r.placeId = :placeId
          AND r.deletedAt IS NULL
        ORDER BY r.createdAt DESC
    """)
    // 목적: 특정 장소의 활성 리뷰만 최신순으로 조회한다
    // 필요 이유: 삭제된 리뷰를 제외하고 사용자에게 보여주기 위해서다
    // 로직: placeId와 deletedAt NULL 조건을 만족하는 리뷰를 정렬해 반환한다
    List<Review> findActiveReviewsByPlaceId(@Param("placeId") Long placeId); // 9월 26일 최종

    @Query("""
        SELECT COUNT(r) FROM Review r
        WHERE r.placeId = :placeId
          AND r.deletedAt IS NULL
    """)
    // 목적: 장소별 활성 리뷰 수를 카운트한다
    // 필요 이유: 통계나 요약 정보에 사용된다
    // 로직: deletedAt이 NULL인 행만 COUNT 한다
    long countActiveReviewsByPlaceId(@Param("placeId") Long placeId); // 9월 26일 최종
}

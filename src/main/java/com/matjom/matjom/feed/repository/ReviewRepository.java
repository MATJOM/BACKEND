package com.matjom.matjom.feed.repository;

import com.matjom.matjom.feed.entity.review.Review;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;

/**
 * 리뷰 엔티티에 대한 데이터 접근 레이어.
 * 사용 목적: 방문/장소/사용자 기준으로 리뷰를 조회하고 존재 여부를 판단한다.
 * 코드 의미: Spring Data JPA 파생 메서드와 JPQL을 활용해 필요한 질의를 선언형으로 작성한다.
 * 기대 결과: 서비스 계층이 중복 로직 없이 리뷰 데이터를 안전하게 읽고 검증한다.
 */
@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {

    /**
     * 방문 ID 기준으로 리뷰를 조회한다.
     * 사용 목적: 방문당 1회 작성 제한을 검증하거나 기존 리뷰를 가져온다.
     * 코드 의미: visitId로 파생 질의를 실행해 Optional로 반환한다.
     * 기대 결과: 이미 작성된 리뷰가 있으면 Optional이 채워져 중복 작성을 막는다.
     */
    Optional<Review> findByVisitId(Long visitId);

    /**
     * 방문 ID로 리뷰 존재 여부를 확인한다.
     * 사용 목적: 저장 전에 빠르게 중복 여부를 판별한다.
     * 코드 의미: COUNT 대신 exists 질의를 사용해 성능과 가독성을 높인다.
     * 기대 결과: 존재하면 true, 없으면 false를 반환해 조건 분기가 단순해진다.
     */
    boolean existsByVisitId(Long visitId);

    @Query("""
        SELECT COUNT(r) FROM Review r
        WHERE r.placeId = :placeId
          AND r.deletedAt IS NULL
    """)
    /**
     * 장소별 활성 리뷰 수를 계산한다.
     * 사용 목적: 상세 화면 통계나 요약 정보 제공에 활용한다.
     * 코드 의미: 삭제되지 않은(re.deletedAt NULL) 레코드만 COUNT 한다.
     * 기대 결과: 현재 노출 가능한 리뷰 개수를 정확히 얻는다.
     */
    long countActiveReviewsByPlaceId(@Param("placeId") Long placeId);

    /**
     * 삭제되지 않은 리뷰가 존재하는지 확인한다.
     * 사용 목적: 신고 등에서 엔티티 로딩 없이 존재 여부만 검증한다.
     * 코드 의미: ID와 deletedAt NULL 조건을 조합한 파생 쿼리를 실행한다.
     * 기대 결과: 활성 리뷰가 남아 있으면 true를 반환한다.
     */
    boolean existsByIdAndDeletedAtIsNull(UUID reviewId);

    /**
     * 방문 ID 목록으로 활성 리뷰를 조회한다.
     * 사용 목적: 여러 방문에 대한 리뷰를 일괄 로딩할 때 사용한다.
     * 코드 의미: deletedAt이 NULL인 조건을 붙여 소프트 삭제 데이터를 제외한다.
     * 기대 결과: 조회된 리스트가 모두 노출 가능한 리뷰로 구성된다.
     */
    List<Review> findByVisitIdInAndDeletedAtIsNull(Collection<Long> visitIds);

    /**
     * 사용자별 최신 순 리뷰를 조회한다.
     * 사용 목적: 마이페이지 등에서 작성 이력을 보여준다.
     * 코드 의미: userId 조건과 생성일 역순 정렬을 조합한다.
     * 기대 결과: 최신 리뷰가 먼저 정렬된 리스트를 반환한다.
     */
    List<Review> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @Query("""
        SELECT r FROM Review r
        WHERE r.placeId = :placeId
          AND r.deletedAt IS NULL
        ORDER BY r.createdAt DESC
    """)
    /**
     * 장소별 활성 리뷰를 최신 순으로 가져온다.
     * 사용 목적: 상세 화면에서 노출할 리뷰 목록을 구성한다.
     * 코드 의미: JPQL로 삭제되지 않은 리뷰만 필터링하고 생성일 역순으로 정렬한다.
     * 기대 결과: 최신 리뷰 리스트를 안정적으로 제공한다.
     */
    List<Review> findActiveReviewsByPlaceId(@Param("placeId") Long placeId);

    /**
     * 장소별 활성 리뷰를 페이지네이션하여 조회한다.
     * 사용 목적: 무한 스크롤이나 일부만 노출하는 API에 활용한다.
     * 코드 의미: placeId와 deletedAt NULL 조건, Pageable 정렬 정보를 결합한다.
     * 기대 결과: 요청한 범위만큼의 최신 리뷰를 반환한다.
     */
    List<Review> findByPlaceIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long placeId, Pageable pageable);
}

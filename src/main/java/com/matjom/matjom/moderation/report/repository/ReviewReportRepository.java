package com.matjom.matjom.moderation.report.repository;

import com.matjom.matjom.moderation.report.entity.ReviewReport;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 리뷰 신고 엔티티용 리포지토리.
 * 사용 목적: 신고 중복 여부 확인과 신고 건수 집계를 처리한다.
 * 코드 의미: Spring Data 파생 메서드로 존재 검사와 카운트 쿼리를 선언한다.
 * 기대 결과: 서비스 계층이 신고 로직을 간결하게 구현한다.
 */
public interface ReviewReportRepository extends JpaRepository<ReviewReport, UUID> {
    /**
     * 특정 리뷰를 특정 사용자가 이미 신고했는지 확인한다.
     * 사용 목적: 중복 신고를 방지한다.
     * 기대 결과: 신고가 존재하면 true를 반환한다.
     */
    boolean existsByReviewIdAndReporterId(UUID reviewId, UUID reporterId);

    /**
     * 리뷰에 대한 누적 신고 횟수를 계산한다.
     * 사용 목적: 임계치 도달 여부를 판단한다.
     * 기대 결과: 신고 건수를 long 값으로 반환한다.
     */
    long countByReviewId(UUID reviewId);
}

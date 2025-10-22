package com.matjom.matjom.moderation.report.service;

import com.matjom.matjom.common.exception.base.FeedException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.feed.repository.ReviewRepository;
import com.matjom.matjom.moderation.report.dto.ReportReviewRequestDTO;
import com.matjom.matjom.moderation.report.entity.ReviewReport;
import com.matjom.matjom.moderation.report.repository.ReviewReportRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리뷰 신고 접수 및 자동 처리 로직을 담당하는 서비스.
 * 사용 목적: 중복 신고 방지, 신고 기록 저장, 누적 신고에 따른 자동 삭제를 수행한다.
 * 코드 의미: 신고 리포지토리와 리뷰 리포지토리를 조합해 트랜잭션 내에서 검증과 후속 조치를 처리한다.
 * 기대 결과: 신고 임계치가 충족되면 리뷰가 자동으로 소프트 삭제되고, 모든 신고가 로그로 남는다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ReviewModerationService {
    private final ReviewReportRepository reviewReportRepository;
    private final ReviewRepository reviewRepository;

    /**
     * 리뷰 신고를 접수하고 후속 조치를 수행한다.
     * 사용 목적: 신고자와 리뷰를 검증한 뒤 신고 기록을 저장하고 임계치 초과 시 자동 삭제한다.
     * 코드 의미: 중복 여부 확인 → 리뷰 존재 검증 → 신고 저장 → 누적 건수 계산 → 조건부 삭제 순으로 처리한다.
     * 기대 결과: 신고가 저장되고 필요 시 리뷰가 소프트 삭제되며, 주요 이벤트가 로깅된다.
     */
    @Transactional
    public void reportReview(UUID reviewId,
                             UUID reporterId,
                             ReportReviewRequestDTO request) {
        if (reviewReportRepository.existsByReviewIdAndReporterId(reviewId, reporterId)) {
            throw new FeedException(ErrorCode.REVIEW_ALREADY_EXISTS, "이미 신고한 리뷰입니다.");
        }
        if (!reviewRepository.existsByIdAndDeletedAtIsNull(reviewId)) {
            throw new FeedException(ErrorCode.REVIEW_NOT_FOUND, "리뷰를 찾을 수 없습니다.");
        }

        ReviewReport saved = reviewReportRepository.save(
                ReviewReport.builder()
                        .reviewId(reviewId)
                        .reporterId(reporterId)
                        .reason(request.getReason())
                        .description(request.getDescription())
                        .build()
        );

        long reportCount = reviewReportRepository.countByReviewId(reviewId);

        if (reportCount >= 3) {
            reviewRepository.findById(reviewId)
                    .filter(review -> !review.isDeleted())
                    .ifPresent(review -> {
                        review.markDeleted();
                        log.info("리뷰 자동 삭제 처리: reviewId={}, reportCount={}", reviewId, reportCount);
                    });
        }

        log.info("리뷰 신고 기록 생성: reviewId={}, reporterId={}, reportId={}, reportCount={}",
                reviewId, reporterId, saved.getId(), reportCount);
    }
}

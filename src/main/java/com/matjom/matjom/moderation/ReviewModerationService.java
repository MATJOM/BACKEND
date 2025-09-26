package com.matjom.matjom.moderation;

import com.matjom.matjom.common.exception.base.FeedException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.feed.entity.review.Review;
import com.matjom.matjom.feed.repository.ReviewRepository;
import com.matjom.matjom.moderation.profanity.ProfanityFilter;
import com.matjom.matjom.moderation.report.dto.ReportReviewRequestDTO;
import com.matjom.matjom.moderation.report.dto.ReportReviewResponseDTO;
import com.matjom.matjom.moderation.report.entity.ReportReason;
import com.matjom.matjom.moderation.report.entity.ReviewReport;
import com.matjom.matjom.moderation.report.repository.ReviewReportRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ReviewModerationService {
    private final ProfanityFilter profanityFilter;
    private final ReviewReportRepository reviewReportRepository;
    private final ReviewRepository reviewRepository;
    private final Clock clock;

    @Value("${moderation.review.hide-threshold:3}")
    private int hideThreshold;

    @Value("${moderation.review.delete-threshold:5}")
    private int deleteThreshold;

    // 9월26일 수정제안: 작성/수정 단계에서 금칙어 입력을 차단합니다.
    public void validateText(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        profanityFilter.validate(text);
    }

    // 9월26일 수정제안: 신고는 이력만 남기고 자동 제재는 하지 않습니다.
    @Transactional
    public ReportReviewResponseDTO reportReview(UUID reviewId,
                                                UUID reporterId,
                                                ReportReviewRequestDTO request) {
        if (reviewReportRepository.existsByReviewIdAndReporterId(reviewId, reporterId)) {
            throw new FeedException(ErrorCode.REVIEW_REPORT_ALREADY_EXISTS, "이미 신고한 리뷰입니다.");
        }

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new FeedException(ErrorCode.REVIEW_NOT_FOUND, "리뷰를 찾을 수 없습니다."));

        ReviewReport saved = reviewReportRepository.save(
                ReviewReport.builder()
                        .reviewId(reviewId)
                        .reporterId(reporterId)
                        .reason(request.getReason())
                        .description(request.getDescription())
                        .build()
        );

        log.info("리뷰 신고 기록 생성: reviewId={}, reporterId={}, reportId={}",
                reviewId, reporterId, saved.getId());

        return ReportReviewResponseDTO.builder()
                .reviewId(reviewId)
                .reportId(saved.getId())
                .reporterId(reporterId)
                .reason(saved.getReason())
                .description(saved.getDescription())
                .reviewStatus(review.getStatus())
                .reportedAt(saved.getCreatedAt())
                .warningCount(review.getWarningCount())
                .build();
    }

    // 9월26일 수정제안: 관리자 수동 경고 발급 API에서 호출하게 설계했습니다.
    @Transactional
    public void issueManualWarning(UUID reviewId, String note) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new FeedException(ErrorCode.REVIEW_NOT_FOUND, "리뷰를 찾을 수 없습니다."));
        OffsetDateTime now = OffsetDateTime.now(clock);
        review.recordWarning(now);
        applyWarningThreshold(review);
        log.info("관리자 경고 발급: reviewId={}, warningCount={}, note={}",
                reviewId, review.getWarningCount(), note);
    }

    // 9월26일 수정제안: 배치/스케줄러가 호출해 기존 리뷰를 재검수합니다.
    @Transactional
    public void scanReviewsAndWarn() {
        List<Review> candidates = reviewRepository.findAllNotDeleted();
        OffsetDateTime now = OffsetDateTime.now(clock);
        candidates.stream()
                .filter(Review::isActive)
                .filter(review -> profanityFilter.contains(review.getText()))
                .forEach(review -> {
                    review.recordWarning(now);
                    applyWarningThreshold(review);
                    log.info("자동 금칙어 경고: reviewId={}, warningCount={}",
                            review.getId(), review.getWarningCount());
                });
    }

    // 9월26일 수정제안: 경고 임계치에 따라 숨김/삭제를 적용합니다.
    private void applyWarningThreshold(Review review) {
        if (review.getWarningCount() >= deleteThreshold) {
            review.delete();
            log.info("경고 누적 삭제 처리: reviewId={}, warningCount={}",
                    review.getId(), review.getWarningCount());
            return;
        }
        if (review.getWarningCount() >= hideThreshold) {
            review.hide();
            review.flag();
            log.info("경고 누적 숨김 처리: reviewId={}, warningCount={}",
                    review.getId(), review.getWarningCount());
            return;
        }
        review.flag();
    }
}


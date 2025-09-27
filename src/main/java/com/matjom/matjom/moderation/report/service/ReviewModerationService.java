package com.matjom.matjom.moderation.report.service;

import com.matjom.matjom.common.exception.base.FeedException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.feed.entity.review.Review;
import com.matjom.matjom.feed.repository.ReviewRepository;
import com.matjom.matjom.feed.repository.UserReadRepository;
import com.matjom.matjom.moderation.profanity.ProfanityFilter;
import com.matjom.matjom.moderation.report.dto.ReportReviewRequestDTO;
import com.matjom.matjom.moderation.report.dto.ReportReviewResponseDTO;
import com.matjom.matjom.moderation.report.entity.ReviewReport;
import com.matjom.matjom.moderation.report.repository.ReviewReportRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ReviewModerationService {
    private static final String UNKNOWN = "알 수 없음";

    private final ProfanityFilter profanityFilter;
    private final ReviewReportRepository reviewReportRepository;
    private final ReviewRepository reviewRepository;
    private final UserReadRepository userReadRepository;

    public void validateText(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        profanityFilter.validate(text);
    }

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

        long reportCount = reviewReportRepository.countByReviewId(reviewId);
        String reporterName = userReadRepository.findNameById(reporterId).orElse(UNKNOWN);

        log.info("리뷰 신고 기록 생성: reviewId={}, reporterId={}, reportId={}, reportCount={}",
                reviewId, reporterId, saved.getId(), reportCount);

        return ReportReviewResponseDTO.builder()
                .reviewId(reviewId)   //신고한 후에 클라이언트가 보는 화면에는 돌려받을 필요는 없다.신고가 완료됐다는 문구만으로도 충분
                .reportId(saved.getId())
                .reporterName(reporterName)
                .reason(saved.getReason())
                .description(saved.getDescription())
                .reportedAt(saved.getCreatedAt())
                .reportCount(reportCount)
                .build();
    }
}

package com.matjom.matjom.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.matjom.matjom.common.exception.base.FeedException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.feed.entity.review.Review;
import com.matjom.matjom.feed.repository.ReviewRepository;
import com.matjom.matjom.feed.repository.UserReadRepository;
import com.matjom.matjom.moderation.profanity.ProfanityFilter;
import com.matjom.matjom.moderation.report.dto.ReportReviewRequestDTO;
import com.matjom.matjom.moderation.report.dto.ReportReviewResponseDTO;
import com.matjom.matjom.moderation.report.entity.ReportReason;
import com.matjom.matjom.moderation.report.entity.ReviewReport;
import com.matjom.matjom.moderation.report.repository.ReviewReportRepository;
import java.util.Optional;
import java.util.UUID;

import com.matjom.matjom.moderation.report.service.ReviewModerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReviewModerationServiceTest {

    @Mock ProfanityFilter profanityFilter;
    @Mock ReviewReportRepository reportRepository;
    @Mock ReviewRepository reviewRepository;
    @Mock UserReadRepository userReadRepository;

    ReviewModerationService service;

    @BeforeEach
    void setUp() {
        service = new ReviewModerationService(profanityFilter, reportRepository, reviewRepository, userReadRepository);
    }

    @Test
    void 금칙어가_있으면_예외() {
        doThrow(new FeedException(ErrorCode.REVIEW_BAD_LANGUAGE, "테스트"))
                .when(profanityFilter).validate("금칙어");

        assertThatThrownBy(() -> service.validateText("금칙어"))
                .isInstanceOf(FeedException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REVIEW_BAD_LANGUAGE);
    }

    @Test
    void 신고시_중복이면_예외() {
        UUID reviewId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();

        when(reportRepository.existsByReviewIdAndReporterId(reviewId, reporterId)).thenReturn(true);

        assertThatThrownBy(() ->
                service.reportReview(reviewId, reporterId,
                        ReportReviewRequestDTO.builder().reason(ReportReason.SPAM).build()))
                .isInstanceOf(FeedException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REVIEW_REPORT_ALREADY_EXISTS);
    }

    @Test
    void 신고시_리뷰상태는_유지되고_신고건수는_증가() {
        UUID reviewId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        Review review = Review.builder()
                .userId(UUID.randomUUID())
                .placeId(1L)
                .visitId(10L)
                .text("리뷰")
                .build();

        when(reportRepository.existsByReviewIdAndReporterId(reviewId, reporterId)).thenReturn(false);
        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review));
        when(reportRepository.save(any(ReviewReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, ReviewReport.class));
        when(reportRepository.countByReviewId(reviewId)).thenReturn(2L);
        when(userReadRepository.findNameById(reporterId)).thenReturn(java.util.Optional.of("신고자"));

        ReportReviewRequestDTO request = ReportReviewRequestDTO.builder()
                .reason(ReportReason.SPAM)
                .description("부적절한 표현")
                .build();

        ReportReviewResponseDTO response = service.reportReview(reviewId, reporterId, request);

        ArgumentCaptor<ReviewReport> reportCaptor = ArgumentCaptor.forClass(ReviewReport.class);
        verify(reportRepository).save(reportCaptor.capture());

        ReviewReport savedReport = reportCaptor.getValue();
        assertThat(savedReport.getReviewId()).isEqualTo(reviewId);
        assertThat(savedReport.getReporterId()).isEqualTo(reporterId);
        assertThat(savedReport.getReason()).isEqualTo(ReportReason.SPAM);
        assertThat(savedReport.getDescription()).isEqualTo("부적절한 표현");

        assertThat(response.getReporterName()).isEqualTo("신고자");
        assertThat(response.getReportCount()).isEqualTo(2L);
    }
}

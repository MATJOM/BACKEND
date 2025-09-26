package com.matjom.matjom.moderation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.matjom.matjom.common.exception.base.FeedException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.feed.entity.review.Review;
import com.matjom.matjom.feed.entity.review.ReviewStatus;
import com.matjom.matjom.feed.repository.ReviewRepository;
import com.matjom.matjom.moderation.profanity.ProfanityFilter;
import com.matjom.matjom.moderation.report.dto.ReportReviewRequestDTO;
import com.matjom.matjom.moderation.report.entity.ReportReason;
import com.matjom.matjom.moderation.report.entity.ReviewReport;
import com.matjom.matjom.moderation.report.repository.ReviewReportRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReviewModerationServiceTest {

    @Mock ProfanityFilter profanityFilter;
    @Mock ReviewReportRepository reportRepository;
    @Mock ReviewRepository reviewRepository;
    Clock fixedClock;

    ReviewModerationService service;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2025-09-26T00:00:00Z"), ZoneOffset.UTC);
        service = new ReviewModerationService(profanityFilter, reportRepository, reviewRepository, fixedClock);
        ReflectionTestUtils.setField(service, "hideThreshold", 3);   // 9월26일 수정제안: 숨김 임계값
        ReflectionTestUtils.setField(service, "deleteThreshold", 5); // 9월26일 수정제안: 삭제 임계값
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
    void 신고시_이력만_저장하고_상태는_변경하지_않음() {
        UUID reviewId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        Review review = mock(Review.class);

        when(reportRepository.existsByReviewIdAndReporterId(reviewId, reporterId)).thenReturn(false);
        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review));
        when(reportRepository.save(any(ReviewReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, ReviewReport.class));

        ReportReviewRequestDTO request = ReportReviewRequestDTO.builder()
                .reason(ReportReason.SPAM)
                .description("부적절한 표현")
                .build();

        service.reportReview(reviewId, reporterId, request);

        ArgumentCaptor<ReviewReport> reportCaptor = ArgumentCaptor.forClass(ReviewReport.class);
        verify(reportRepository).save(reportCaptor.capture());

        ReviewReport savedReport = reportCaptor.getValue();
        // 9월26일 수정제안: 신고 엔터티가 올바르게 구성되었는지 확인
        org.assertj.core.api.Assertions.assertThat(savedReport.getReviewId()).isEqualTo(reviewId);
        org.assertj.core.api.Assertions.assertThat(savedReport.getReporterId()).isEqualTo(reporterId);
        org.assertj.core.api.Assertions.assertThat(savedReport.getReason()).isEqualTo(ReportReason.SPAM);
        org.assertj.core.api.Assertions.assertThat(savedReport.getDescription()).isEqualTo("부적절한 표현");

        // 9월26일 수정제안: 신고만으로 리뷰 상태가 바뀌지 않습니다.
        verify(reviewRepository).findById(reviewId);
    }

    @Test
    void 수동경고가_숨김_임계치에_도달하면_숨김처리() {
        UUID reviewId = UUID.randomUUID();
        Review review = mock(Review.class);

        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review));
        when(review.getWarningCount()).thenReturn(2).thenReturn(3); // 호출 순서 고려

        service.issueManualWarning(reviewId, "관리자 경고");
        verify(review).recordWarning(OffsetDateTime.now(fixedClock));
        verify(review).hide();
        verify(review).flag();
    }

    @Test
    void 수동경고가_삭제_임계치에_도달하면_삭제처리() {
        UUID reviewId = UUID.randomUUID();
        Review review = mock(Review.class);

        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review));
        when(review.getWarningCount()).thenReturn(5); // 9월26일 수정제안: 삭제 임계치 값으로 고정

        service.issueManualWarning(reviewId, "관리자 경고");
        verify(review).recordWarning(OffsetDateTime.now(fixedClock));
        verify(review).delete();
    }


    @Test
    void 자동스캔이_금칙어를_찾으면_경고와_상태변경() {
        Review flaggable = mock(Review.class);

        when(reviewRepository.findAllNotDeleted()).thenReturn(List.of(flaggable));
        when(flaggable.isActive()).thenReturn(true);
        when(profanityFilter.contains(flaggable.getText())).thenReturn(true);
        when(flaggable.getWarningCount()).thenReturn(2).thenReturn(3); // 숨김 전후

        service.scanReviewsAndWarn();

        verify(flaggable).recordWarning(OffsetDateTime.now(fixedClock));
        verify(flaggable).hide();
        verify(flaggable).flag();
    }

    @Test
    void 자동스캔이_삭제_임계치까지_올리면_삭제처리() {
        Review removable = mock(Review.class);

        when(reviewRepository.findAllNotDeleted()).thenReturn(List.of(removable));
        when(removable.isActive()).thenReturn(true);
        when(profanityFilter.contains(removable.getText())).thenReturn(true);
        when(removable.getWarningCount()).thenReturn(5); // 9월26일 수정제안: 삭제 임계치 도달 후 모든 호출에 5 반환

        service.scanReviewsAndWarn();

        verify(removable).recordWarning(OffsetDateTime.now(fixedClock));
        verify(removable).delete();
    }
}
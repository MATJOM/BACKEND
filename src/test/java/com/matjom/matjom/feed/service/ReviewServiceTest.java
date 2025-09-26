package com.matjom.matjom.feed.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.matjom.matjom.feed.dto.response.EligibilityCheckResponseDTO;
import com.matjom.matjom.feed.repository.ReviewRepository;
import com.matjom.matjom.feed.service.VisitEligibilityChecker.VisitEligibilityStatus;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private VisitEligibilityChecker visitEligibilityChecker;

    @InjectMocks
    private ReviewService reviewService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final Long PLACE_ID = 1L;
    private static final Long VISIT_ID = 100L;

    @Test
    @DisplayName("도착한 방문이고 중복 리뷰가 없으면 작성 가능")
    void checkReviewEligibilityReturnsEligibleWhenArrived() {
        given(visitEligibilityChecker.check(USER_ID, VISIT_ID)).willReturn(VisitEligibilityStatus.ARRIVED);
        given(reviewRepository.existsByVisitId(VISIT_ID)).willReturn(false);

        EligibilityCheckResponseDTO response = reviewService.checkReviewEligibility(USER_ID, PLACE_ID, VISIT_ID);

        assertAll(
                () -> assertThat(response.getEligible()).isTrue(),
                () -> assertThat(response.getVisitExists()).isTrue(),
                () -> assertThat(response.getVisitArrived()).isTrue(),
                () -> assertThat(response.getAlreadyWritten()).isFalse()
        );
        verify(reviewRepository).existsByVisitId(VISIT_ID);
    }

    @Test
    @DisplayName("방문 기록이 없으면 작성 불가")
    void checkReviewEligibilityReturnsNotEligibleWhenVisitMissing() {
        given(visitEligibilityChecker.check(USER_ID, VISIT_ID)).willReturn(VisitEligibilityStatus.NOT_FOUND);

        EligibilityCheckResponseDTO response = reviewService.checkReviewEligibility(USER_ID, PLACE_ID, VISIT_ID);

        assertAll(
                () -> assertThat(response.getEligible()).isFalse(),
                () -> assertThat(response.getVisitExists()).isFalse(),
                () -> assertThat(response.getVisitArrived()).isFalse()
        );
    }

    @Test
    @DisplayName("이미 리뷰가 있다면 작성 불가")
    void checkReviewEligibilityReturnsNotEligibleWhenAlreadyWritten() {
        given(visitEligibilityChecker.check(USER_ID, VISIT_ID)).willReturn(VisitEligibilityStatus.ARRIVED);
        given(reviewRepository.existsByVisitId(VISIT_ID)).willReturn(true);

        EligibilityCheckResponseDTO response = reviewService.checkReviewEligibility(USER_ID, PLACE_ID, VISIT_ID);

        assertAll(
                () -> assertThat(response.getEligible()).isFalse(),
                () -> assertThat(response.getVisitExists()).isTrue(),
                () -> assertThat(response.getAlreadyWritten()).isTrue()
        );
    }
}

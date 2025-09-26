package com.matjom.matjom.feed.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

import com.matjom.matjom.common.exception.base.FeedException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.feed.dto.request.ReviewCreateRequestDTO;
import com.matjom.matjom.feed.dto.response.ReviewResponseDTO;
import com.matjom.matjom.feed.entity.review.Review;
import com.matjom.matjom.feed.repository.ReviewRepository;
import com.matjom.matjom.feed.service.VisitEligibilityChecker.VisitEligibilityStatus;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ReviewServiceIntegrationTest {

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private ReviewRepository reviewRepository;

    @MockBean
    private VisitEligibilityChecker visitEligibilityChecker;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final Long PLACE_ID = 1L;
    private static final Long VISIT_ID = 10L;

    @Test
    @DisplayName("리뷰 작성 성공 시 저장된다")
    void createReviewPersistsWhenEligible() {
        given(visitEligibilityChecker.check(USER_ID, VISIT_ID)).willReturn(VisitEligibilityStatus.ARRIVED);
        ReviewCreateRequestDTO request = new ReviewCreateRequestDTO(PLACE_ID, VISIT_ID, "맛있어요");

        ReviewResponseDTO response = reviewService.createReview(USER_ID, request);

        Optional<Review> saved = reviewRepository.findByVisitId(VISIT_ID);
        assertThat(saved).isPresent();
        assertThat(saved.get().getText()).isEqualTo("맛있어요");
        assertThat(response.getReviewId()).isEqualTo(saved.get().getId());
        assertThat(response.getReviewerName()).isEqualTo("알 수 없음"); // 9월 26일 최종: 기본 이름 반환
        assertThat(response.getPlaceName()).isEqualTo("알 수 없음");
    }

    @Test
    @DisplayName("방문 정보가 없으면 REVIEW_NOT_ALLOWED 예외")
    void createReviewFailsWhenVisitMissing() {
        given(visitEligibilityChecker.check(USER_ID, VISIT_ID)).willReturn(VisitEligibilityStatus.NOT_FOUND);
        ReviewCreateRequestDTO request = new ReviewCreateRequestDTO(PLACE_ID, VISIT_ID, "맛없어요");

        FeedException exception = assertThrows(FeedException.class, () -> reviewService.createReview(USER_ID, request));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REVIEW_NOT_ALLOWED);
    }
}

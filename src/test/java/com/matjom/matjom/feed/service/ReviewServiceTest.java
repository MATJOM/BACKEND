package com.matjom.matjom.feed.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.matjom.matjom.feed.dto.assembler.ReviewResponseAssembler;
import com.matjom.matjom.feed.dto.request.ReviewCreateRequestDTO;
import com.matjom.matjom.feed.dto.response.ReviewResponseDTO;
import com.matjom.matjom.feed.repository.ReviewRepository;
import com.matjom.matjom.common.exception.base.FeedException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.moderation.profanity.ProfanityFilter;
import com.matjom.matjom.feed.entity.review.Review;
import java.time.OffsetDateTime;
import java.util.Optional;
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

    @Mock
    private ReviewResponseAssembler reviewResponseAssembler;

    @Mock
    private ProfanityFilter profanityFilter;

    @InjectMocks
    private ReviewService reviewService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final Long PLACE_ID = 1L;
    private static final Long VISIT_ID = 100L;

    @Test
    @DisplayName("도착하지 않았으면 리뷰 작성이 거부된다")
    // 목적: ARRIVED 전 사용자에게 작성 기회를 주지 않는지 검증
    // 상황: 방문 자격 검사에서 false를 반환하도록 모킹
    // 기대: REVIEW_NOT_ALLOWED 예외가 발생하고 저장 로직은 실행되지 않는다
    void createReviewFailsWhenNotArrived() {
        given(visitEligibilityChecker.isArrived(USER_ID, VISIT_ID)).willReturn(false);

        ReviewCreateRequestDTO request = new ReviewCreateRequestDTO(PLACE_ID, VISIT_ID, "맛있어요");

        FeedException exception = assertThrows(FeedException.class,
                () -> reviewService.createReview(USER_ID, request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REVIEW_NOT_ALLOWED);
        verify(reviewRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("이미 리뷰가 있을 때 중복 작성이 거부된다")
    // 목적: 동일 방문에 대한 중복 작성 방지 로직 검증
    // 상황: ARRIVED 상태지만 저장소에서 이미 존재한다고 응답하도록 세팅
    // 기대: REVIEW_ALREADY_EXISTS 예외가 발생한다
    void createReviewFailsWhenAlreadyWritten() {
        given(visitEligibilityChecker.isArrived(USER_ID, VISIT_ID)).willReturn(true);
        given(reviewRepository.existsByVisitId(VISIT_ID)).willReturn(true);

        ReviewCreateRequestDTO request = new ReviewCreateRequestDTO(PLACE_ID, VISIT_ID, "맛있어요");

        FeedException exception = assertThrows(FeedException.class,
                () -> reviewService.createReview(USER_ID, request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REVIEW_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("visitId 없이 요청하면 최신 ARRIVED 방문을 사용한다")
    // 목적: 프런트가 visitId를 생략했을 때 자동 매칭이 동작하는지 검증
    // 상황: 최신 ARRIVED 방문 ID를 리포지토리가 반환하도록 모킹하고 저장 결과를 검증
    // 기대: 저장이 정상 수행되고 assembler가 호출된다
    void createReviewResolvesLatestVisitWhenNotProvided() {
        given(visitEligibilityChecker.findLatestArrivedVisitId(USER_ID, PLACE_ID))
                .willReturn(Optional.of(VISIT_ID));
        given(reviewRepository.existsByVisitId(VISIT_ID)).willReturn(false);
        Review persisted = Review.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .placeId(PLACE_ID)
                .visitId(VISIT_ID)
                .text("맛있어요")
                .build();
        given(reviewRepository.save(any(Review.class))).willReturn(persisted);
        given(reviewResponseAssembler.toDto(persisted)).willReturn(
                ReviewResponseDTO.builder()
                        .reviewerName("사용자")
                        .text("맛있어요")
                        .createdAt(OffsetDateTime.now())
                        .build()
        );

        ReviewCreateRequestDTO request = new ReviewCreateRequestDTO(PLACE_ID, null, "맛있어요");

        ReviewResponseDTO response = reviewService.createReview(USER_ID, request);

        assertThat(response.getText()).isEqualTo("맛있어요");
        verify(reviewRepository).save(any(Review.class));
    }
}

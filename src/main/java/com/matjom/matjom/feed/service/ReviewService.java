package com.matjom.matjom.feed.service;

import com.matjom.matjom.feed.dto.request.ReviewCreateRequestDTO;
import com.matjom.matjom.feed.dto.request.ReviewUpdateRequestDTO;
import com.matjom.matjom.feed.dto.response.EligibilityCheckResponseDTO;
import com.matjom.matjom.feed.dto.response.ReviewResponseDTO;
import com.matjom.matjom.feed.entity.review.Review;
import com.matjom.matjom.feed.entity.review.ReviewStatus;
import com.matjom.matjom.feed.repository.ReviewRepository;
import com.matjom.matjom.common.exception.base.FeedException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.moderation.ReviewModerationService;
import com.matjom.matjom.moderation.report.dto.ReportReviewRequestDTO;
import com.matjom.matjom.moderation.report.entity.ReportReason;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.matjom.matjom.feed.event.ReviewCreatedEvent;
import com.matjom.matjom.moderation.ReviewModerationService;
import org.springframework.context.ApplicationEventPublisher;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ReviewService {
    private final ReviewRepository reviewRepository;
    private final ReviewModerationService reviewModerationService;
    private final ApplicationEventPublisher eventPublisher; // 9월26일 재수정: 통계/알림 연계를 위한 이벤트 발행
    // TODO: VisitService 주입 필요 (visits 테이블 조회용)

    /**
     * 리뷰 작성 자격 확인
     * UC-Feed-01: 기회 확인 로직 통합
     */
    public EligibilityCheckResponseDTO checkReviewEligibility(UUID userId, Long placeId, Long visitId) {
        log.info("리뷰 작성 자격 확인: userId={}, placeId={}, visitId={}", userId, placeId, visitId);

        // 1. visits 테이블 조회: arrived_at NOT NULL AND state = 'ARRIVED' 확인
        // TODO: VisitService.findByIdAndUserId(visitId, userId) 호출
        // Visit visit = visitService.findByIdAndUserId(visitId, userId);
        // if (visit == null) {
        //     return EligibilityCheckResponse.notEligible("방문 기록을 찾을 수 없습니다", visitId, false, false, false);
        // }
        // if (visit.getArrivedAt() == null || !visit.getState().equals("ARRIVED")) {
        //     return EligibilityCheckResponse.notEligible("도착 확인 후 작성 가능", visitId, false, false, true);
        // }

        // 2. 당일 내 방문인지 확인 (date_kst = today)
        // LocalDate visitDate = visit.getArrivedAt().atZone(ZoneId.of("Asia/Seoul")).toLocalDate();
        // if (!visitDate.equals(LocalDate.now(ZoneId.of("Asia/Seoul")))) {
        //     return EligibilityCheckResponse.notEligible("당일 방문에만 작성 가능", visitId, true, false, false);
        // }

        // 3. 이미 리뷰 작성했는지 확인
        boolean alreadyWritten = reviewRepository.existsByVisitId(visitId);
        if (alreadyWritten) {
            return EligibilityCheckResponseDTO.notEligible("이미 리뷰를 작성하셨습니다", visitId, true, true, true);
        }

        // 모든 조건 통과
        return EligibilityCheckResponseDTO.eligible(visitId);
    }

    /**
     * 리뷰 작성
     * UC-Feed-01: 기회 확인 통합 방식
     */
    @Transactional
    public ReviewResponseDTO createReview(UUID userId, ReviewCreateRequestDTO request) {
        reviewModerationService.validateText(request.getText());
        log.info("리뷰 작성 시작: userId={}, placeId={}, visitId={}",
                maskUserId(userId), request.getPlaceId(), request.getVisitId());

        // 1. 기회 확인 로직
        EligibilityCheckResponseDTO eligibility = checkReviewEligibility(
                userId, request.getPlaceId(), request.getVisitId()
        );

        if (!eligibility.getEligible()) {
            log.info("리뷰 작성 실패: userId={}, visitId={}, reason={}",
                    maskUserId(userId), request.getVisitId(), eligibility.getReason());
            if (!eligibility.getVisitArrived()) {
                throw new FeedException(ErrorCode.ARRIVAL_NOT_CONFIRMED, eligibility.getReason());
            }
            if (eligibility.getAlreadyWritten()) {
                throw new FeedException(ErrorCode.REVIEW_ALREADY_EXISTS, eligibility.getReason());
            }
            throw new FeedException(ErrorCode.REVIEW_NOT_ALLOWED, eligibility.getReason());
        }

        // 2. 비속어 필터링
        reviewModerationService.validateText(request.getText());

        // 3. 리뷰 생성 및 저장
        Review review = Review.builder()
                .userId(userId)
                .placeId(request.getPlaceId())
                .visitId(request.getVisitId())
                .text(request.getText())
                .status(ReviewStatus.ACTIVE)
                .flagged(false)
                .build();

        Review savedReview = reviewRepository.save(review);

        // 4. 통계 업데이트 (비동기 또는 이벤트 발생)
        // 9월26일 재수정: 통계/알림 파이프라인 연동을 위한 이벤트 발행
        eventPublisher.publishEvent(new ReviewCreatedEvent(
                savedReview.getId(),
                savedReview.getUserId(),
                savedReview.getPlaceId(),
                savedReview.getCreatedAt()
        ));

        log.info("리뷰 작성 완료: reviewId={}, userId={}",
                savedReview.getId(), maskUserId(userId));
        return ReviewResponseDTO.from(savedReview);
    }

    /**
     * 리뷰 수정
     */
    @Transactional
    public ReviewResponseDTO updateReview(UUID userId, UUID reviewId, ReviewUpdateRequestDTO request) {
        reviewModerationService.validateText(request.getText());
        log.info("리뷰 수정 요청: userId={}, reviewId={}",
                maskUserId(userId), reviewId);
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new FeedException(ErrorCode.REVIEW_NOT_FOUND, "리뷰를 찾을 수 없습니다"));

        // 작성자 확인
        if (!review.getUserId().equals(userId)) {
            throw new FeedException(ErrorCode.FORBIDDEN, "자신의 리뷰만 수정할 수 있습니다");
        }

        // 삭제된 리뷰는 수정 불가
        if (review.isDeleted()) {
            throw new FeedException(ErrorCode.REVIEW_NOT_ALLOWED, "삭제된 리뷰는 수정할 수 없습니다");
        }

        // 비속어 필터링
        reviewModerationService.validateText(request.getText());

        review.setText(request.getText());

        log.info("리뷰 수정 완료: reviewId={}",
                reviewId);
        return ReviewResponseDTO.from(review);
    }

    @Transactional
    public void reportReview(UUID userId, UUID reviewId,
                             ReportReason reason, String description) {
        ReportReviewRequestDTO request = ReportReviewRequestDTO.builder()
                .reason(reason)
                .description(description)
                .build();

        reviewModerationService.reportReview(reviewId, userId, request);
    }

    /**
     * 리뷰 삭제 (소프트 삭제)
     */
    @Transactional
    public void deleteReview(UUID userId, UUID reviewId) {
        log.info("리뷰 삭제 요청: userId={}, reviewId={}",
                maskUserId(userId), reviewId);
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new FeedException(ErrorCode.REVIEW_NOT_FOUND, "리뷰를 찾을 수 없습니다"));

        // 작성자 확인
        if (!review.getUserId().equals(userId)) {
            throw new FeedException(ErrorCode.FORBIDDEN, "자신의 리뷰만 삭제할 수 있습니다");
        }

        review.delete(); // BaseEntity의 markDeleted() + status 변경

        log.info("리뷰 삭제 완료: reviewId={}", reviewId);
    }

    /**
     * 사용자의 리뷰 목록 조회
     */
    public List<ReviewResponseDTO> getUserReviews(UUID userId) {
        List<Review> reviews = reviewRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return reviews.stream()
                .filter(review -> !review.isDeleted()) // 삭제된 리뷰 제외
                .map(ReviewResponseDTO::from)
                .collect(Collectors.toList());
    }

    /**
     * 장소별 활성 리뷰 조회
     */
    public List<ReviewResponseDTO> getPlaceReviews(Long placeId) {
        List<Review> reviews = reviewRepository.findActiveReviewsByPlaceId(placeId);
        return reviews.stream()
                .map(ReviewResponseDTO::from)
                .collect(Collectors.toList());
    }

    /**
     * 사용자가 특정 장소에 작성한 리뷰들 (추천 알고리즘용)
     */
    public List<ReviewResponseDTO> getUserPlaceReviews(UUID userId, Long placeId) {
        List<Review> reviews = reviewRepository.findByUserIdAndPlaceId(userId, placeId);
        return reviews.stream()
                .map(ReviewResponseDTO::from)
                .collect(Collectors.toList());
    }

    private String maskUserId(UUID userId) {
        String value = userId.toString();
        return value.substring(0, 8) + "****";
    }
}


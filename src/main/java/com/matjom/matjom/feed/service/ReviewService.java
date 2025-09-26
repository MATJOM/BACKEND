package com.matjom.matjom.feed.service;

import com.matjom.matjom.feed.dto.request.ReviewCreateRequestDTO;
import com.matjom.matjom.feed.dto.request.ReviewUpdateRequestDTO;
import com.matjom.matjom.feed.dto.response.EligibilityCheckResponseDTO;
import com.matjom.matjom.feed.dto.response.ReviewResponseDTO;
import com.matjom.matjom.feed.service.ReviewResponseAssembler;
import com.matjom.matjom.feed.entity.review.Review;
import com.matjom.matjom.feed.repository.ReviewRepository;
import com.matjom.matjom.feed.service.VisitEligibilityChecker;
import com.matjom.matjom.feed.service.VisitEligibilityChecker.VisitEligibilityStatus; // 9월 26일 최종: 방문 자격 확인 재사용
import com.matjom.matjom.common.exception.base.FeedException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ReviewService {
    private final ReviewRepository reviewRepository;
    private final VisitEligibilityChecker visitEligibilityChecker; // 9월 26일 최종: 방문 자격 검증 컴포넌트
    private final ReviewResponseAssembler reviewResponseAssembler; // 9월 26일 최종: 이름을 포함한 DTO 조립
    // TODO: 비속어 필터링 서비스 주입 필요

    /**
     * 리뷰 작성 자격 확인
     * UC-Feed-01: 기회 확인 로직 통합
     */
    public EligibilityCheckResponseDTO checkReviewEligibility(UUID userId, Long placeId, Long visitId) {
        log.info("리뷰 작성 자격 확인: userId={}, placeId={}, visitId={}", userId, placeId, visitId);

        VisitEligibilityStatus visitStatus = visitEligibilityChecker.check(userId, visitId); // 9월 26일 최종: 방문 존재/도착 여부 확인

        if (visitStatus == VisitEligibilityStatus.NOT_FOUND) {
            return EligibilityCheckResponseDTO.notEligible("방문 기록을 찾을 수 없습니다", visitId,
                    false, false, false, false); // 9월 26일 최종: 방문 없음
        }

        if (visitStatus == VisitEligibilityStatus.NOT_ARRIVED) {
            return EligibilityCheckResponseDTO.notEligible("도착 확인 후 작성 가능합니다", visitId,
                    true, false, false, true); // 9월 26일 최종: 아직 도착하지 않음
        }

        if (reviewRepository.existsByVisitId(visitId)) {
            return EligibilityCheckResponseDTO.notEligible("이미 리뷰를 작성하셨습니다", visitId,
                    true, true, true, true); // 9월 26일 최종: 리뷰 중복 작성 방지
        }

        return EligibilityCheckResponseDTO.eligible(visitId); // 9월 26일 최종: 모든 조건 통과
    }

    /**
     * 리뷰 작성
     * UC-Feed-01: 기회 확인 통합 방식
     */
    @Transactional
    public ReviewResponseDTO createReview(UUID userId, ReviewCreateRequestDTO request) {
        log.info("리뷰 작성 시작: userId={}, placeId={}, visitId={}",
                maskUserId(userId), request.getPlaceId(), request.getVisitId());

        // 1. 기회 확인 로직
        EligibilityCheckResponseDTO eligibility = checkReviewEligibility(
                userId, request.getPlaceId(), request.getVisitId()
        );

        if (!eligibility.getEligible()) {
            log.info("리뷰 작성 실패: userId={}, visitId={}, reason={}",
                    maskUserId(userId), request.getVisitId(), eligibility.getReason());
            if (!Boolean.TRUE.equals(eligibility.getVisitExists())) {
                throw new FeedException(ErrorCode.REVIEW_NOT_ALLOWED, eligibility.getReason()); // 9월 26일 최종: 방문 미존재
            }
            if (!Boolean.TRUE.equals(eligibility.getVisitArrived())) {
                throw new FeedException(ErrorCode.ARRIVAL_NOT_CONFIRMED, eligibility.getReason()); // 9월 26일 최종: 미도착
            }
            if (Boolean.TRUE.equals(eligibility.getAlreadyWritten())) {
                throw new FeedException(ErrorCode.REVIEW_ALREADY_EXISTS, eligibility.getReason()); // 9월 26일 최종: 중복 작성
            }
            throw new FeedException(ErrorCode.REVIEW_NOT_ALLOWED, eligibility.getReason());
        }

        // 2. 비속어 필터링
        // TODO: 비속어 필터링 서비스 연동
        // if (profanityFilterService.containsProfanity(request.getText())) {
        //     // 9월24일 재수정: IllegalArgumentException -> FeedException 변경
        //     throw new FeedException(ErrorCode.REVIEW_BAD_LANGUAGE);
        // }


        // 3. 리뷰 생성 및 저장
        Review review = Review.builder()
                .userId(userId)
                .placeId(request.getPlaceId())
                .visitId(request.getVisitId())
                .text(request.getText())
                .build(); // 9월 26일 최종: 최소 필드만 설정

        Review savedReview = reviewRepository.save(review);

        // 4. 통계 업데이트 (비동기 또는 이벤트 발생)
        // TODO: 통계 업데이트 이벤트 발생
        // applicationEventPublisher.publishEvent(new ReviewCreatedEvent(savedReview));

        log.info("리뷰 작성 완료: reviewId={}, userId={}",
                savedReview.getId(), maskUserId(userId));
        return reviewResponseAssembler.toDto(savedReview); // 9월 26일 최종: 이름 포함 응답
    }

    /**
     * 리뷰 수정
     */
    @Transactional
    public ReviewResponseDTO updateReview(UUID userId, UUID reviewId, ReviewUpdateRequestDTO request) {
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
        // TODO: 비속어 필터링 서비스 연동
        // if (profanityFilterService.containsProfanity(request.getText())) {
        //     // 9월24일 재수정: IllegalArgumentException -> FeedException 변경
        //     throw new FeedException(ErrorCode.REVIEW_BAD_LANGUAGE);
        // }

        review.setText(request.getText());

        log.info("리뷰 수정 완료: reviewId={}",
                reviewId);
        return reviewResponseAssembler.toDto(review); // 9월 26일 최종
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
                .map(reviewResponseAssembler::toDto) // 9월 26일 최종
                .collect(Collectors.toList());
    }

    /**
     * 장소별 활성 리뷰 조회
     */
    public List<ReviewResponseDTO> getPlaceReviews(Long placeId) {
        List<Review> reviews = reviewRepository.findActiveReviewsByPlaceId(placeId);
        return reviews.stream()
                .map(reviewResponseAssembler::toDto) // 9월 26일 최종
                .collect(Collectors.toList());
    }

    /**
     * 사용자가 특정 장소에 작성한 리뷰들 (추천 알고리즘용)
     */
    public List<ReviewResponseDTO> getUserPlaceReviews(UUID userId, Long placeId) {
        List<Review> reviews = reviewRepository.findByUserIdAndPlaceId(userId, placeId);
        return reviews.stream()
                .map(reviewResponseAssembler::toDto) // 9월 26일 최종
                .collect(Collectors.toList());
    }

    private String maskUserId(UUID userId) {
        String value = userId.toString();
        return value.substring(0, 8) + "****";
    }
}


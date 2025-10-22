package com.matjom.matjom.feed.service;

import com.matjom.matjom.common.exception.base.FeedException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.feed.dto.request.ReviewCreateRequestDTO;
import com.matjom.matjom.feed.dto.request.ReviewUpdateRequestDTO;
import com.matjom.matjom.feed.dto.response.ReviewResponseDTO;
import com.matjom.matjom.feed.entity.review.Review;
import com.matjom.matjom.feed.repository.ReviewRepository;
import com.matjom.matjom.feed.repository.UserReadRepository;
import com.matjom.matjom.moderation.profanity.ProfanityFilter;
import com.matjom.matjom.visit.service.VisitEligibilityChecker;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;

/**
 * 리뷰 작성/수정/삭제 및 조회를 담당하는 서비스.
 * 사용 목적: 비즈니스 규칙(방문 검증, 금칙어, 시간 제한)을 적용해 리뷰 데이터를 관리한다.
 * 코드 의미: 리포지토리와 방문 검증기, 금칙어 필터를 조합해 트랜잭션 단위로 작업한다.
 * 기대 결과: 올바른 조건에서만 리뷰가 생성·수정되고, 조회 시에는 활성 데이터만 전달된다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final VisitEligibilityChecker visitEligibilityChecker;
    private final ProfanityFilter profanityFilter;
    private final UserReadRepository userReadRepository;

    private static final String UNKNOWN_REVIEWER = "알 수 없음";

    /**
     * 리뷰를 신규 작성한다.
     * 사용 목적: 방문 후 24시간 내에 첫 리뷰를 저장한다.
     * 코드 의미: 방문 검증 → 중복 체크 → 금칙어 검증 → 엔티티 저장 순으로 처리한다.
     * 기대 결과: 성공 시 저장된 리뷰를 DTO로 변환해 반환한다.
     */
    @Transactional
    public ReviewResponseDTO createReview(UUID userId, ReviewCreateRequestDTO request) {
        log.info("리뷰 작성 시작: userId={}, placeId={}, visitId={}", userId, request.getPlaceId(), request.getVisitId());

        Long visitId = resolveArrivedVisitId(userId, request.getPlaceId(), request.getVisitId());
        validateReviewCreationWindow(userId, visitId);

        if (reviewRepository.existsByVisitId(visitId)) {
            throw new FeedException(ErrorCode.REVIEW_ALREADY_EXISTS, "이미 리뷰를 작성하셨습니다");
        }

        profanityFilter.validate(request.getText());
        String reviewerName = loadReviewerName(userId);

        Review review = Review.builder()
                .userId(userId)
                .placeId(request.getPlaceId())
                .visitId(visitId)
                .userName(reviewerName)
                .text(request.getText())
                .build();

        Review saved = reviewRepository.save(review);
        log.info("리뷰 작성 완료: reviewId={}, userId={}", saved.getId(), userId);
        return ReviewResponseDTO.of(saved);
    }

    /**
     * 기존 리뷰를 수정한다.
     * 사용 목적: 작성자가 허용 시간 내에 본문을 변경할 수 있도록 한다.
     * 코드 의미: 작성자 일치/삭제 여부/시간 제한을 확인하고 금칙어 필터 적용 후 텍스트를 갱신한다.
     * 기대 결과: 수정된 리뷰를 DTO로 변환해 반환한다.
     */
    @Transactional
    public ReviewResponseDTO updateReview(UUID userId, UUID reviewId, ReviewUpdateRequestDTO request) {
        log.info("리뷰 수정 요청: userId={}, reviewId={}", userId, reviewId);

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new FeedException(ErrorCode.REVIEW_NOT_FOUND, "리뷰를 찾을 수 없습니다"));

        if (!review.getUserId().equals(userId)) {
            throw new FeedException(ErrorCode.FORBIDDEN, "자신의 리뷰만 수정할 수 있습니다");
        }
        if (review.isDeleted()) {
            throw new FeedException(ErrorCode.REVIEW_NOT_ALLOWED, "삭제된 리뷰는 수정할 수 없습니다");
        }

        validateReviewUpdateWindow(review);
        profanityFilter.validate(request.getText());

        review.setText(request.getText());
        log.info("리뷰 수정 완료: reviewId={}", reviewId);
        return ReviewResponseDTO.of(review);
    }

    /**
     * 리뷰를 소프트 삭제한다.
     * 사용 목적: 작성자가 더 이상 노출을 원치 않는 리뷰를 숨긴다.
     * 코드 의미: 작성자 검증 후 엔티티의 삭제 플래그를 설정한다.
     * 기대 결과: 삭제 완료 후 별도 데이터 반환 없이 OK 응답을 보낼 수 있다.
     */
    @Transactional
    public void deleteReview(UUID userId, UUID reviewId) {
        log.info("리뷰 삭제 요청: userId={}, reviewId={}", userId, reviewId);

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new FeedException(ErrorCode.REVIEW_NOT_FOUND, "리뷰를 찾을 수 없습니다"));

        if (!review.getUserId().equals(userId)) {
            throw new FeedException(ErrorCode.FORBIDDEN, "자신의 리뷰만 삭제할 수 있습니다");
        }

        review.markDeleted();
        log.info("리뷰 삭제 완료: reviewId={}", reviewId);
    }

    /**
     * 장소별 최신 리뷰를 제한 개수만큼 조회한다.
     * 사용 목적: 상세 페이지 등에서 최근 리뷰 요약을 보여준다.
     * 코드 의미: PageRequest로 상위 N개의 활성 리뷰를 가져와 DTO로 변환한다.
     * 기대 결과: 시간 역순으로 제한된 수만큼의 리뷰 응답 리스트를 반환한다.
     */
    public List<ReviewResponseDTO> getLatestPlaceReviews(Long placeId, int limit) {
        return reviewRepository.findByPlaceIdAndDeletedAtIsNullOrderByCreatedAtDesc(placeId, PageRequest.of(0, limit)).stream()
                .map(ReviewResponseDTO::of)
                .toList();
    }

    /**
     * 장소별 모든 활성 리뷰를 조회한다.
     * 사용 목적: 전체 리뷰 탭 등에서 사용한다.
     * 코드 의미: 삭제되지 않은 리뷰 목록을 불러와 DTO로 매핑한다.
     * 기대 결과: 최신순으로 정렬된 리뷰 리스트가 반환된다.
     */
    public java.util.List<ReviewResponseDTO> getPlaceReviews(Long placeId) {
        return reviewRepository.findActiveReviewsByPlaceId(placeId).stream()
                .map(ReviewResponseDTO::of)
                .toList();
    }

    /**
     * 사용자의 리뷰 이력을 최신순으로 조회한다.
     * 사용 목적: 마이페이지 등에서 작성 내역을 보여준다.
     * 코드 의미: 사용자 ID로 조회한 뒤 소프트 삭제된 항목을 제외하고 DTO로 변환한다.
     * 기대 결과: 본인이 작성해 현재 남아 있는 리뷰 리스트를 반환한다.
     */
    public java.util.List<ReviewResponseDTO> getUserReviews(UUID userId) {
        return reviewRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(review -> !review.isDeleted())
                .map(ReviewResponseDTO::of)
                .toList();
    }

    /**
     * 리뷰 작성에 사용할 방문 ID를 결정한다.
     * 사용 목적: 명시된 방문이 있으면 검증하고, 없으면 최신 방문을 탐색한다.
     * 코드 의미: 방문 자격 검증기를 통해 도착 여부를 확인하고 ID를 반환한다.
     * 기대 결과: 유효한 방문 ID가 있으면 반환하고, 없으면 예외를 던진다.
     */
    private Long resolveArrivedVisitId(UUID userId, Long placeId, Long requestedVisitId) {
        if (requestedVisitId != null) {
            visitEligibilityChecker.findArrivedAt(userId, requestedVisitId)
                    .orElseThrow(() -> new FeedException(ErrorCode.REVIEW_NOT_ALLOWED, "도착한 방문이 없습니다"));
            return requestedVisitId;
        }

        return visitEligibilityChecker.findLatestArrivedVisitId(userId, placeId)
                .orElseThrow(() -> new FeedException(ErrorCode.REVIEW_NOT_ALLOWED, "도착한 방문이 없습니다"));
    }

    /**
     * 리뷰 작성 가능 시간을 검증한다.
     * 사용 목적: 방문 후 24시간 이내에만 리뷰를 작성하도록 제한한다.
     * 코드 의미: 방문 도착 시각을 조회한 뒤 24시간 경과 여부를 확인한다.
     * 기대 결과: 허용 시간이 지났다면 예외를 발생시켜 작성 요청을 차단한다.
     */
    private void validateReviewCreationWindow(UUID userId, Long visitId) {
        OffsetDateTime arrivedAt = visitEligibilityChecker.findArrivedAt(userId, visitId)
                .orElseThrow(() -> new FeedException(ErrorCode.REVIEW_NOT_ALLOWED, "도착한 방문이 없습니다"));
        if (isPast24Hours(arrivedAt)) {
            throw new FeedException(ErrorCode.REVIEW_NOT_ALLOWED, "방문 후 24시간이 지나 리뷰를 작성할 수 없습니다");
        }
    }

    /**
     * 리뷰 수정 가능 시간을 검증한다.
     * 사용 목적: 작성 후 24시간 내에만 수정하도록 제한한다.
     * 코드 의미: 작성 시각을 확인해 24시간이 지났으면 예외를 던진다.
     * 기대 결과: 허용 시간이 지났다면 수정이 차단된다.
     */
    private void validateReviewUpdateWindow(Review review) {
        OffsetDateTime createdAt = review.getCreatedAt();
        if (createdAt == null || isPast24Hours(createdAt)) {
            throw new FeedException(ErrorCode.REVIEW_NOT_ALLOWED, "리뷰 수정 가능 시간이 지났습니다");
        }
    }

    /**
     * 기준 시각이 24시간을 초과했는지 확인한다.
     * 사용 목적: 생성/수정 시간 제한 계산을 재사용한다.
     * 코드 의미: 기준 시각에 24시간을 더해 현재 시간과 비교한다.
     * 기대 결과: 24시간이 지났다면 true, 아니면 false를 반환한다.
     */
    private boolean isPast24Hours(OffsetDateTime baseTime) {
        return baseTime.plusHours(24).isBefore(OffsetDateTime.now());
    }

    /**
     * 리뷰 작성자 이름을 조회한다.
     * 사용 목적: 응답 DTO에서 사용자 이름을 노출한다.
     * 코드 의미: 사용자 리포지토리에서 이름을 조회하고 없거나 공백이면 기본값을 사용한다.
     * 기대 결과: 항상 비어 있지 않은 이름 문자열을 반환한다.
     */
    private String loadReviewerName(UUID userId) {
        return userReadRepository.findNameById(userId)
                .filter(name -> !name.isBlank())
                .orElse(UNKNOWN_REVIEWER);
    }
}

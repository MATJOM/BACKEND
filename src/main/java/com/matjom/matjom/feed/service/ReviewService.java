package com.matjom.matjom.feed.service;

import com.matjom.matjom.feed.dto.request.ReviewCreateRequestDTO;
import com.matjom.matjom.feed.dto.request.ReviewUpdateRequestDTO;
import com.matjom.matjom.feed.dto.response.ReviewResponseDTO;
import com.matjom.matjom.feed.dto.assembler.ReviewResponseAssembler;
import com.matjom.matjom.feed.entity.review.Review;
import com.matjom.matjom.feed.repository.ReviewRepository;
import com.matjom.matjom.feed.service.VisitEligibilityChecker;
import com.matjom.matjom.common.exception.base.FeedException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.moderation.profanity.ProfanityFilter;
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
    private final ProfanityFilter profanityFilter;
    /**
     * 리뷰 작성 자격 확인
     * UC-Feed-01: 기회 확인 로직 통합
     */ //여기는 왜DTO로 돌려주는지 이유 알아오기
    /**
     * 리뷰 작성
     * UC-Feed-01: 기회 확인 통합 방식
     */
    @Transactional
    // 목적: 방문 완료 사용자의 리뷰를 생성하고 저장한다
    // 필요 이유: ARRIVED 방문마다 한 번의 후기 기회를 보장해 서비스 신뢰도를 높인다
    // 로직: 자격·중복·비속어를 순차 검증한 뒤 엔티티를 생성해 저장하고 DTO로 변환한다
    public ReviewResponseDTO createReview(UUID userId, ReviewCreateRequestDTO request) {
        log.info("리뷰 작성 시작: userId={}, placeId={}, visitId={}",
                maskUserId(userId), request.getPlaceId(), request.getVisitId());

        Long visitId = resolveArrivedVisitId(userId, request.getPlaceId(), request.getVisitId()); // 9월 30일 최종: visitId 자동 매칭

        if (reviewRepository.existsByVisitId(visitId)) {
            throw new FeedException(ErrorCode.REVIEW_ALREADY_EXISTS, "이미 리뷰를 작성하셨습니다");
        }

        // 2. 비속어 필터링
        profanityFilter.validate(request.getText());


        // 3. 리뷰 생성 및 저장
        Review review = Review.builder()
                .userId(userId)
                .placeId(request.getPlaceId())
                .visitId(visitId)
                .text(request.getText())
                .build(); // 9월 26일 최종: 최소 필드만 설정

        Review savedReview = reviewRepository.save(review);

        log.info("리뷰 작성 완료: reviewId={}, userId={}",
                savedReview.getId(), maskUserId(userId));
        return reviewResponseAssembler.toDto(savedReview); // 9월 26일 최종: 이름 포함 응답
    }

    /**
     * 리뷰 수정
     */
    @Transactional
    // 목적: 사용자가 본인의 리뷰 텍스트를 수정한다
    // 필요 이유: 오타 수정이나 추가 정보 기입 등 사후 보완 요구를 반영한다
    // 로직: 작성자와 삭제 여부를 확인하고 비속어 필터 후 내용을 갱신해 DTO로 반환한다
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
        profanityFilter.validate(request.getText());

        review.setText(request.getText());

        log.info("리뷰 수정 완료: reviewId={}",
                reviewId);
        return reviewResponseAssembler.toDto(review); // 9월 26일 최종
    }

    /**
     * 리뷰 삭제 (소프트 삭제)
     */
    @Transactional
    // 목적: 리뷰를 물리 삭제 대신 소프트 삭제 처리한다
    // 필요 이유: 감사/통계용으로 기록은 남기면서 사용자 노출은 막기 위함이다
    // 로직: 작성자 일치 여부를 확인하고 BaseEntity의 markDeleted를 호출한다
    public void deleteReview(UUID userId, UUID reviewId) {
        log.info("리뷰 삭제 요청: userId={}, reviewId={}",
                maskUserId(userId), reviewId);
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new FeedException(ErrorCode.REVIEW_NOT_FOUND, "리뷰를 찾을 수 없습니다"));

        // 작성자 확인
        if (!review.getUserId().equals(userId)) {
            throw new FeedException(ErrorCode.FORBIDDEN, "자신의 리뷰만 삭제할 수 있습니다");
        }

        review.markDeleted(); // BaseEntity 삭제 시간만 설정해 소프트 딜리트 처리

        log.info("리뷰 삭제 완료: reviewId={}", reviewId);
    }

    /**
     * 사용자의 리뷰 목록 조회
     */
    // 목적: 특정 사용자가 작성한 리뷰 이력을 제공한다
    // 필요 이유: 마이페이지 등에서 자신의 활동 내역을 확인할 수 있어야 한다
    // 로직: 작성 시각 내림차순으로 조회해 삭제되지 않은 리뷰만 DTO로 변환한다
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
    // 목적: 장소 상세 화면에 활성 리뷰를 나열한다
    // 필요 이유: 방문 의사 결정을 돕는 최신 후기 정보를 제공한다
    // 로직: 활성 상태 리뷰를 조회해 DTO 리스트로 만들어 반환한다
    public List<ReviewResponseDTO> getPlaceReviews(Long placeId) {
        List<Review> reviews = reviewRepository.findActiveReviewsByPlaceId(placeId);
        return reviews.stream()
                .map(reviewResponseAssembler::toDto) // 9월 26일 최종
                .collect(Collectors.toList());
    }

    // 목적: 로그에 노출되는 사용자 ID를 부분 마스킹한다
    // 필요 이유: 운영 로그에서 개인정보를 최소한으로 노출하기 위함이다
    // 로직: UUID 문자열의 앞 8자리만 남기고 나머지를 별표로 치환한다
    private String maskUserId(UUID userId) {
        String value = userId.toString();
        return value.substring(0, 8) + "****";
    }

    // 목적: 요청에 visitId가 없거나 잘못된 경우 최신 ARRIVED 방문을 찾아낸다
    // 필요 이유: 프런트에서 placeId만 전달해도 리뷰를 작성할 수 있도록 하기 위함이다
    // 로직: 명시된 visitId는 ARRIVED 여부를 검증하고, 없으면 리포지토리에서 최신 ARRIVED 방문 ID를 조회한다
    private Long resolveArrivedVisitId(UUID userId, Long placeId, Long requestedVisitId) {
        if (requestedVisitId != null) {
            if (!visitEligibilityChecker.isArrived(userId, requestedVisitId)) { // 9월 30일 최종: ARRIVED 여부 확인
                throw new FeedException(ErrorCode.REVIEW_NOT_ALLOWED, "도착한 방문이 없습니다");
            }
            return requestedVisitId;
        }

        return visitEligibilityChecker.findLatestArrivedVisitId(userId, placeId)
                .orElseThrow(() -> new FeedException(ErrorCode.REVIEW_NOT_ALLOWED, "도착한 방문이 없습니다"));
    }
}

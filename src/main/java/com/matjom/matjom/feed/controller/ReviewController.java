package com.matjom.matjom.feed.controller;

import com.matjom.matjom.common.response.ApiResponse;
import com.matjom.matjom.feed.dto.request.ReviewCreateRequestDTO;
import com.matjom.matjom.feed.dto.request.ReviewUpdateRequestDTO;
import com.matjom.matjom.feed.dto.response.ReviewResponseDTO;
import com.matjom.matjom.feed.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {
    private final ReviewService reviewService;

    /**
     * 리뷰 작성
     * POST /api/v1/reviews
     * 내부에서 자격 확인 후 작성 또는 에러 반환
     */
    @PostMapping
    // 목적: 사용자가 방문 후 남기는 리뷰를 생성한다
    // 필요 이유: ARRIVED 방문에 한해 후기 작성 기회를 제공하기 위함이다
    // 로직: JWT에서 추출한 userId와 요청 DTO를 서비스에 넘겨 자격 검증 후 저장 결과를 응답으로 감싼다
    public ApiResponse<ReviewResponseDTO> createReview(
            @Valid @RequestBody ReviewCreateRequestDTO request,
            @RequestAttribute (value = "userId", required = true)UUID userId// JWT에서 추출된 userId
    ) {
        return ApiResponse.ok(reviewService.createReview(userId, request));
    }

    /**
     * 리뷰 수정
     * PUT /api/v1/reviews/{reviewId}
     */
    @PutMapping("/{reviewId}")
    // 목적: 사용자가 본인이 작성한 리뷰 내용을 수정한다
    // 필요 이유: 방문 경험을 보완하거나 정정할 수 있어야 사용자 만족도가 높아진다
    // 로직: 경로 파라미터의 reviewId와 사용자 식별자를 받아 서비스에서 작성자 검증·비속어 필터 이후 갱신한 DTO를 반환한다
    public ApiResponse<ReviewResponseDTO> updateReview(
            @PathVariable UUID reviewId,
            @Valid @RequestBody ReviewUpdateRequestDTO request,
            @RequestAttribute(value = "userId", required = true) UUID userId
    ) {
        return ApiResponse.ok(reviewService.updateReview(userId, reviewId, request));
    }

    /**
     * 리뷰 삭제
     * DELETE /api/v1/reviews/{reviewId}
     */
    @DeleteMapping("/{reviewId}")
    // 목적: 사용자가 자신의 리뷰를 소프트 삭제한다
    // 필요 이유: 잘못 작성한 리뷰를 숨기거나 철회할 수 있어야 한다
    // 로직: 서비스에 삭제를 위임해 작성자 검증 후 BaseEntity의 삭제 시각을 갱신한다
    public ApiResponse<Void> deleteReview(
            @PathVariable UUID reviewId,
            @RequestAttribute(value = "userId", required = true) UUID userId
    ) {
        reviewService.deleteReview(userId, reviewId);
        return ApiResponse.ok();
    }

    /**
     * 사용자의 리뷰 목록 조회
     * GET /api/v1/reviews/my
     */
    @GetMapping("/my")
    // 목적: 사용자가 남긴 모든 리뷰 이력을 확인한다
    // 필요 이유: 본인이 작성한 리뷰 현황을 쉽게 관리하도록 돕는다
    // 로직: JWT 사용자 식별자로 서비스에서 최신순 조회 후 삭제되지 않은 리뷰만 DTO로 반환한다
    public ApiResponse<List<ReviewResponseDTO>> getMyReviews(
            @RequestAttribute(value = "userId", required = true) UUID userId) {
        return ApiResponse.ok(reviewService.getUserReviews(userId));
    }

    /**
     * 장소별 리뷰 조회
     * GET /api/v1/reviews?placeId=1
     */
    @GetMapping
    // 목적: 특정 장소에 등록된 활성 리뷰 목록을 조회한다
    // 필요 이유: 장소 상세 화면에서 다른 방문자의 후기 정보를 제공하기 위함이다
    // 로직: 요청 파라미터의 placeId로 서비스에 위임해 활성 리뷰를 DTO 리스트로 변환한다
    public ApiResponse<List<ReviewResponseDTO>> getPlaceReviews(@RequestParam Long placeId) {
        return ApiResponse.ok(reviewService.getPlaceReviews(placeId));
    }

}

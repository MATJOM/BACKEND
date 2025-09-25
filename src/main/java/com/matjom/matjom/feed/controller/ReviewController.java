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
    public ApiResponse<List<ReviewResponseDTO>> getMyReviews(
            @RequestAttribute(value = "userId", required = true) UUID userId) {
        return ApiResponse.ok(reviewService.getUserReviews(userId));
    }

    /**
     * 장소별 리뷰 조회
     * GET /api/v1/reviews?placeId=1
     */
    @GetMapping
    public ApiResponse<List<ReviewResponseDTO>> getPlaceReviews(@RequestParam Long placeId) {
        return ApiResponse.ok(reviewService.getPlaceReviews(placeId));
    }

    /**
     * 사용자가 특정 장소에 작성한 리뷰들
     * GET /api/v1/reviews/my/place/{placeId}
     */
    @GetMapping("/my/place/{placeId}")
    public ApiResponse<List<ReviewResponseDTO>> getMyPlaceReviews(
            @PathVariable Long placeId,
            @RequestAttribute(value = "userId", required = true) UUID userId
    ) {
        return ApiResponse.ok(reviewService.getUserPlaceReviews(userId, placeId));
    }
}

package com.matjom.matjom.feed.controller;

import com.matjom.matjom.common.response.ApiResponse;
import com.matjom.matjom.feed.dto.request.ReviewCreateRequestDTO;
import com.matjom.matjom.feed.dto.request.ReviewUpdateRequestDTO;
import com.matjom.matjom.feed.dto.response.ReviewResponseDTO;
import com.matjom.matjom.feed.service.ReviewService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {
    private final ReviewService reviewService;

    @PostMapping
    public ApiResponse<ReviewResponseDTO> createReview(
            @Valid @RequestBody ReviewCreateRequestDTO request,
            @RequestAttribute(value = "userId", required = true) UUID userId
    ) {
        return ApiResponse.ok(reviewService.createReview(userId, request));
    }

    @PutMapping("/{reviewId}")
    public ApiResponse<ReviewResponseDTO> updateReview(
            @PathVariable UUID reviewId,
            @Valid @RequestBody ReviewUpdateRequestDTO request,
            @RequestAttribute(value = "userId", required = true) UUID userId
    ) {
        return ApiResponse.ok(reviewService.updateReview(userId, reviewId, request));
    }

    @DeleteMapping("/{reviewId}")
    public ApiResponse<Void> deleteReview(
            @PathVariable UUID reviewId,
            @RequestAttribute(value = "userId", required = true) UUID userId
    ) {
        reviewService.deleteReview(userId, reviewId);
        return ApiResponse.ok();
    }
}

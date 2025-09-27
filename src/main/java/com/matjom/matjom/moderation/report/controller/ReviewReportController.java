package com.matjom.matjom.moderation.report.controller;

import com.matjom.matjom.common.response.ApiResponse;
import com.matjom.matjom.moderation.report.service.ReviewModerationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import com.matjom.matjom.moderation.report.dto.ReportReviewRequestDTO;
import com.matjom.matjom.moderation.report.dto.ReportReviewResponseDTO;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewReportController {

    private final ReviewModerationService reviewModerationService;

    @PostMapping("/{reviewId}/reports")
    public ApiResponse<ReportReviewResponseDTO> reportReview(@PathVariable UUID reviewId,
                                                             @RequestAttribute("userId") UUID userId,
                                                             @Valid @RequestBody ReportReviewRequestDTO request) {
        ReportReviewResponseDTO response = reviewModerationService.reportReview(reviewId, userId, request);
        return ApiResponse.ok(response);
    }
}
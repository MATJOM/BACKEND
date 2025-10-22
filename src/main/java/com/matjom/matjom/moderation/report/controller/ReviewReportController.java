package com.matjom.matjom.moderation.report.controller;

import com.matjom.matjom.common.response.ApiResponse;
import com.matjom.matjom.common.security.CustomUserDetails;
import com.matjom.matjom.moderation.report.dto.ReportReviewRequestDTO;
import com.matjom.matjom.moderation.report.service.ReviewModerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 리뷰 신고를 접수하는 moderation 컨트롤러.
 * 사용 목적: 인증된 사용자가 리뷰 문제를 신고하도록 HTTP 엔드포인트를 제공한다.
 * 코드 의미: 요청 파라미터/본문을 검증하고 서비스 계층을 호출한 뒤 공통 응답 포맷을 반환한다.
 * 기대 결과: 신고 접수가 성공하면 HTTP 200 OK가 내려간다.
 */
@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
@Tag(name = "Moderation - Review Reports", description = "리뷰 신고 API")
@SecurityRequirement(name = "bearerAuth")
public class ReviewReportController {

    private final ReviewModerationService reviewModerationService;

    /**
     * 리뷰 신고를 접수한다.
     * 사용 목적: 신고 사유와 설명을 받아 moderation 서비스로 전달한다.
     * 코드 의미: 사용자 ID를 검증하고 DTO를 서비스에 넘긴 뒤 빈 응답을 감싼다.
     * 기대 결과: 신고가 저장되면 성공 응답, 오류 시 예외가 발생한다.
     */
    @Operation(summary = "리뷰 신고", description = "리뷰에 문제가 있는 경우 사유와 함께 신고합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "신고 접수 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "이미 신고했거나 요청이 잘못됨"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "리뷰를 찾을 수 없음")
    })
    @PostMapping("/{reviewId}/reports")
    public ApiResponse<Void> reportReview(@AuthenticationPrincipal CustomUserDetails user,
                                          @Parameter(description = "신고 대상 리뷰 ID", required = true)
                                          @PathVariable UUID reviewId,
                                          @Valid @RequestBody ReportReviewRequestDTO request) {
        UUID userId = requireUserId(user);
        reviewModerationService.reportReview(reviewId, userId, request);
        return ApiResponse.ok();
    }

    /**
     * 인증 정보에서 사용자 ID를 추출하고 검증한다.
     * 사용 목적: Null 안전성을 확보해 모든 신고 요청에서 일관된 검증을 수행한다.
     * 코드 의미: `Objects.requireNonNull`로 인증 객체와 userId 필드를 차례대로 확인한다.
     * 기대 결과: 인증 정보가 누락되면 즉시 예외가 발생한다.
     */
    private UUID requireUserId(CustomUserDetails user) {
        UUID userId = Objects.requireNonNull(user, "인증 정보가 필요합니다.").getUserId();
        return Objects.requireNonNull(userId, "사용자 ID가 필요합니다.");
    }
}

/*
프런트에서는 신고 버튼을 눌렀을 때 API를 호출하고, 응답의 success 값만 확인해서 토스트(또는 alert)를 띄우면 됩니다.
예시로 React + axios + 토스트 컴포넌트를 쓴다고 가정하면 아래처럼 작성할 수 있습니다.

import axios from 'axios';
import { toast } from '@/components/ui/toast';

async function reportReview(reviewId: string, payload: { reason: string; description?: string }) {
  try {
    const response = await axios.post<ApiResponse<null>>(
      `/api/v1/reviews/${reviewId}/reports`,
      payload,
      { headers: { Authorization: `Bearer ${token}` } }
    );

    if (response.data.success) {
      toast.success('신고가 접수되었습니다.');
    } else {
      toast.error(response.data.error?.message ?? '신고 처리 중 문제가 발생했습니다.');
    }
  } catch (error) {
    toast.error('네트워크 오류로 신고에 실패했습니다.');
  }
}
 */

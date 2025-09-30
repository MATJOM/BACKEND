package com.matjom.matjom.moderation.report.controller;

import com.matjom.matjom.common.response.ApiResponse;
import com.matjom.matjom.moderation.report.service.ReviewModerationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import com.matjom.matjom.moderation.report.dto.ReportReviewRequestDTO;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewReportController {

    private final ReviewModerationService reviewModerationService;

    @PostMapping("/{reviewId}/reports")
    public ApiResponse<Void> reportReview(@PathVariable UUID reviewId,
                                          @RequestAttribute("userId") UUID userId,
                                          @Valid @RequestBody ReportReviewRequestDTO request) {
        reviewModerationService.reportReview(reviewId, userId, request);
        return ApiResponse.ok();
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
      toast.success('신고가 완료되었습니다. 신고 내용은 관리자에게 전달되었고, 확인 후 조치 예정입니다.');
    } else {
      toast.error(response.data.error?.message ?? '신고 처리 중 문제가 발생했습니다.');
    }
  } catch (error) {
    toast.error('네트워크 오류로 신고에 실패했습니다.');
  }
}
 */
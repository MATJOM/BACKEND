package com.matjom.matjom.feed.controller;

import com.matjom.matjom.common.response.ApiResponse;
import com.matjom.matjom.feed.dto.request.DailyLikeCreateRequestDTO;
import com.matjom.matjom.feed.service.DailyLikeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/likes")
@RequiredArgsConstructor
public class DailyLikeController {
    private final DailyLikeService dailyLikeService;

    /**
     * 좋아요 등록
     * POST /api/v1/daily-likes
     * 내부에서 자격 확인 후 등록 또는 에러 반환
     */
    @PostMapping
    // 목적: 방문을 완료한 사용자가 하루 한 번 좋아요를 남기도록 처리한다
    // 필요 이유: 장소에 대한 긍정적 피드백을 수집해 통계와 추천에 활용하기 위함이다
    // 로직: 사용자 식별자와 요청 DTO를 서비스에 전달해 자격 검증 후 저장된 좋아요 정보를 반환한다
    public ApiResponse<Void> createLike(
            @Valid @RequestBody DailyLikeCreateRequestDTO request,
            @RequestAttribute UUID userId
    ) {
        dailyLikeService.createLike(userId, request);
        return ApiResponse.ok();
    }

    /**
     * 좋아요 취소
     * DELETE /api/v1/daily-likes/{likeId}
     */
    @DeleteMapping("/{likeId}")
    // 목적: 사용자가 이미 누른 좋아요를 취소한다
    // 필요 이유: 실수나 의사 변경 시 즉시 상태를 되돌릴 수 있어야 한다
    // 로직: 서비스에서 소유자 검증 후 상태를 CANCELLED로 바꾸고 빈 응답을 반환한다
    public ApiResponse<Void> cancelLike(
            @PathVariable UUID likeId,
            @RequestAttribute UUID userId
    ) {
        dailyLikeService.cancelLike(userId, likeId);
        return ApiResponse.ok();
    }

    /**
     * 좋아요 재등록
     * PUT /api/v1/daily-likes/{likeId}
     */
    @PutMapping("/{likeId}")
    // 목적: 취소했던 좋아요를 다시 활성화한다
    // 필요 이유: 동일 방문 내 재평가가 가능해야 사용자 경험이 유연해진다
    // 로직: 서비스에서 사용자와 좋아요 소유를 확인한 뒤 상태를 ACTIVE로 되돌린다
    public ApiResponse<Void> reactivateLike(
            @PathVariable UUID likeId,
            @RequestAttribute UUID userId
    ) {
        dailyLikeService.reactivateLike(userId, likeId);
        return ApiResponse.ok();
    }
}

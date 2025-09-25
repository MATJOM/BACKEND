package com.matjom.matjom.feed.controller;

import com.matjom.matjom.common.response.ApiResponse;
import com.matjom.matjom.feed.dto.request.DailyLikeCreateRequestDTO;
import com.matjom.matjom.feed.dto.response.DailyLikeResponseDTO;
import com.matjom.matjom.feed.service.DailyLikeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
    public DailyLikeResponseDTO createLike(
            @Valid @RequestBody DailyLikeCreateRequestDTO request,
            @RequestAttribute UUID userId
    ) {
        return dailyLikeService.createLike(userId, request);
    }

    /**
     * 좋아요 취소
     * DELETE /api/v1/daily-likes/{likeId}
     */
    @DeleteMapping("/{likeId}")
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
    public DailyLikeResponseDTO reactivateLike(
            @PathVariable UUID likeId,
            @RequestAttribute UUID userId
    ) {
        return dailyLikeService.reactivateLike(userId, likeId);
    }

    /**
     * 사용자가 특정 장소에 누른 좋아요들
     * GET /api/v1/daily-likes/my/place/{placeId}
     */
    @GetMapping("/my/place/{placeId}")
    public List<DailyLikeResponseDTO> getMyPlaceLikes(
            @PathVariable Long placeId,
            @RequestAttribute UUID userId
    ) {
        return dailyLikeService.getUserPlaceLikes(userId, placeId);
    }
}

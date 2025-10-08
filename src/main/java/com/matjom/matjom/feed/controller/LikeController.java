package com.matjom.matjom.feed.controller;

import com.matjom.matjom.common.response.ApiResponse;
import com.matjom.matjom.feed.dto.request.LikeCreateRequestDTO;
import com.matjom.matjom.feed.dto.response.LikeStatusResponseDTO;
import com.matjom.matjom.feed.service.LikeService;
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
@RequestMapping("/api/v1/likes")
@RequiredArgsConstructor
public class LikeController {

    private final LikeService likeService;

    @PostMapping
    public ApiResponse<LikeStatusResponseDTO> createLike(
            @Valid @RequestBody LikeCreateRequestDTO request,
            @RequestAttribute UUID userId
    ) {
        return ApiResponse.ok(likeService.createLike(userId, request));
    }

    @DeleteMapping("/{likeId}")
    public ApiResponse<LikeStatusResponseDTO> cancelLike(
            @PathVariable UUID likeId,
            @RequestAttribute UUID userId
    ) {
        return ApiResponse.ok(likeService.cancelLike(userId, likeId));
    }

    @PutMapping("/{likeId}")
    public ApiResponse<LikeStatusResponseDTO> reactivateLike(
            @PathVariable UUID likeId,
            @RequestAttribute UUID userId
    ) {
        return ApiResponse.ok(likeService.reactivateLike(userId, likeId));
    }
}

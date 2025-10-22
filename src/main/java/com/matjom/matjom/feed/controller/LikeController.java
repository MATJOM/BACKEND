package com.matjom.matjom.feed.controller;

import com.matjom.matjom.common.response.ApiResponse;
import com.matjom.matjom.common.security.CustomUserDetails;
import com.matjom.matjom.feed.dto.request.LikeCreateRequestDTO;
import com.matjom.matjom.feed.dto.response.LikeStatusResponseDTO;
import com.matjom.matjom.feed.service.LikeService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 좋아요 생성/취소/재활성화 API를 제공하는 컨트롤러.
 * 사용 목적: 사용자 방문 데이터를 바탕으로 좋아요 상태를 관리한다.
 * 코드 의미: 인증 정보에서 사용자 ID를 추출해 서비스 계층 호출 후 공통 응답으로 감싼다.
 * 기대 결과: 좋아요 토글 흐름이 REST 방식으로 안정적으로 동작한다.
 */
@RestController
@RequestMapping("/api/v1/likes")
@RequiredArgsConstructor
@Tag(name = "Feed - Likes", description = "좋아요 생성/취소 API")
@SecurityRequirement(name = "bearerAuth")
public class LikeController {

    private final LikeService likeService;

    /**
     * 좋아요를 신규 등록한다.
     * 사용 목적: 방문 기록에 대해 첫 좋아요를 기록한다.
     * 코드 의미: 인증된 사용자 ID와 검증된 요청 DTO를 서비스로 전달한다.
     * 기대 결과: 성공 시 활성 좋아요 상태가 응답으로 반환된다.
     */
    @Operation(summary = "좋아요 등록", description = "최근 도착 방문을 기준으로 좋아요를 생성합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "좋아요 등록 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "좋아요 등록 조건 미충족"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 좋아요가 존재")
    })
    @PostMapping
    public ApiResponse<LikeStatusResponseDTO> createLike(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody LikeCreateRequestDTO request
    ) {
        UUID userId = requireUserId(user);
        return ApiResponse.ok(likeService.createLike(userId, request));
    }

    /**
     * 등록된 좋아요를 취소한다.
     * 사용 목적: 사용자가 더 이상 좋아요를 유지하지 않을 때 상태를 변경한다.
     * 코드 의미: 사용자와 좋아요 ID를 검증한 뒤 서비스에서 취소 로직을 실행한다.
     * 기대 결과: 성공 시 취소된 상태 정보가 응답된다.
     */
    @Operation(summary = "좋아요 취소", description = "등록된 좋아요를 취소합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "좋아요 취소 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "취소 가능 시간이 지났거나 이미 취소됨"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "좋아요를 찾을 수 없음")
    })
    @DeleteMapping("/{likeId}")
    public ApiResponse<LikeStatusResponseDTO> cancelLike(
            @AuthenticationPrincipal CustomUserDetails user,
            @Parameter(description = "취소할 좋아요 ID", required = true)
            @PathVariable UUID likeId
    ) {
        UUID userId = requireUserId(user);
        return ApiResponse.ok(likeService.cancelLike(userId, likeId));
    }

    /**
     * 취소된 좋아요를 다시 활성화한다.
     * 사용 목적: 동일 방문에 대해 좋아요를 재설정할 때 사용한다.
     * 코드 의미: 인증 정보와 좋아요 ID를 확인한 뒤 서비스의 재활성화 로직을 호출한다.
     * 기대 결과: 성공 시 ACTIVE 상태의 좋아요 정보가 응답된다.
     */
    @Operation(summary = "좋아요 재활성화", description = "취소된 좋아요를 다시 활성화합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "좋아요 재활성화 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "재활성화 조건 미충족"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "좋아요를 찾을 수 없음")
    })
    @PutMapping("/{likeId}")
    public ApiResponse<LikeStatusResponseDTO> reactivateLike(
            @AuthenticationPrincipal CustomUserDetails user,
            @Parameter(description = "재활성화할 좋아요 ID", required = true)
            @PathVariable UUID likeId
    ) {
        UUID userId = requireUserId(user);
        return ApiResponse.ok(likeService.reactivateLike(userId, likeId));
    }

    /**
     * 인증 객체에서 사용자 ID를 추출하고 검증한다.
     * 사용 목적: 모든 핸들러에서 Null 안전성을 확보한다.
     * 코드 의미: `Objects.requireNonNull`로 인증 정보와 ID를 차례대로 확인한다.
     * 기대 결과: 인증 정보가 없으면 즉시 예외를 발생시켜 잘못된 호출을 막는다.
     */
    private UUID requireUserId(CustomUserDetails user) {
        UUID userId = Objects.requireNonNull(user, "인증 정보가 필요합니다.").getUserId();
        return Objects.requireNonNull(userId, "사용자 ID가 필요합니다.");
    }
}

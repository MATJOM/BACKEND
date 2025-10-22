package com.matjom.matjom.feed.controller;

import com.matjom.matjom.common.response.ApiResponse;
import com.matjom.matjom.common.security.CustomUserDetails;
import com.matjom.matjom.feed.dto.request.ReviewCreateRequestDTO;
import com.matjom.matjom.feed.dto.request.ReviewUpdateRequestDTO;
import com.matjom.matjom.feed.dto.response.ReviewResponseDTO;
import com.matjom.matjom.feed.service.ReviewService;
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
 * 리뷰 작성/수정/삭제 API를 제공하는 컨트롤러.
 * 사용 목적: 인증된 사용자가 자신의 방문 기록을 바탕으로 리뷰를 관리하도록 한다.
 * 코드 의미: 요청 본문을 검증하고 서비스 계층에 위임한 뒤 공통 응답 포맷으로 결과를 반환한다.
 * 기대 결과: REST 규약에 맞춰 리뷰 CRUD 흐름이 안정적으로 처리된다.
 */
@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
@Tag(name = "Feed - Reviews", description = "리뷰 작성과 관리 API")
@SecurityRequirement(name = "bearerAuth")
public class ReviewController {
    private final ReviewService reviewService;

    /**
     * 신규 리뷰를 작성한다.
     * 사용 목적: 방문 기록에 대한 첫 리뷰를 생성한다.
     * 코드 의미: 인증 사용자 ID를 추출하고 DTO 검증을 마친 뒤 서비스 호출 결과를 감싼다.
     * 기대 결과: 성공 시 작성된 리뷰 데이터가 응답으로 전달된다.
     */
    @Operation(summary = "리뷰 작성", description = "도착한 방문 정보를 기반으로 리뷰를 신규 작성합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "리뷰 작성 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "리뷰 작성 조건 미충족"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "대상 방문 또는 사용자 없음")
    })
    @PostMapping
    public ApiResponse<ReviewResponseDTO> createReview(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody ReviewCreateRequestDTO request
    ) {
        UUID userId = requireUserId(user);
        return ApiResponse.ok(reviewService.createReview(userId, request));
    }

    /**
     * 기존 리뷰를 수정한다.
     * 사용 목적: 작성자가 허용된 시간 내에 내용을 변경하도록 지원한다.
     * 코드 의미: 사용자와 리뷰 ID를 검증한 뒤 수정 DTO를 서비스로 전달한다.
     * 기대 결과: 성공 시 최신 내용이 적용된 리뷰 응답을 반환한다.
     */
    @Operation(summary = "리뷰 수정", description = "작성한 리뷰를 24시간 이내에 수정합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "리뷰 수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "수정 가능 시간이 지났거나 요청이 잘못됨"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "본인 리뷰가 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "리뷰를 찾을 수 없음")
    })
    @PutMapping("/{reviewId}")
    public ApiResponse<ReviewResponseDTO> updateReview(
            @AuthenticationPrincipal CustomUserDetails user,
            @Parameter(description = "수정할 리뷰 ID", required = true)
            @PathVariable UUID reviewId,
            @Valid @RequestBody ReviewUpdateRequestDTO request
    ) {
        UUID userId = requireUserId(user);
        return ApiResponse.ok(reviewService.updateReview(userId, reviewId, request));
    }

    /**
     * 리뷰를 소프트 삭제한다.
     * 사용 목적: 작성자가 더 이상 노출을 원치 않는 리뷰를 숨긴다.
     * 코드 의미: 인증 정보와 리뷰 ID를 검증한 뒤 서비스 레이어에 삭제를 위임한다.
     * 기대 결과: 성공 시 본문 없는 OK 응답을 반환하고, 이후 조회에서 제외된다.
     */
    @Operation(summary = "리뷰 삭제", description = "작성한 리뷰를 삭제(소프트 삭제)합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "리뷰 삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "본인 리뷰가 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "리뷰를 찾을 수 없음")
    })
    @DeleteMapping("/{reviewId}")
    public ApiResponse<Void> deleteReview(
            @AuthenticationPrincipal CustomUserDetails user,
            @Parameter(description = "삭제할 리뷰 ID", required = true)
            @PathVariable UUID reviewId
    ) {
        UUID userId = requireUserId(user);
        reviewService.deleteReview(userId, reviewId);
        return ApiResponse.ok();
    }

    /**
     * 인증 객체에서 사용자 ID를 꺼내 검증한다.
     * 사용 목적: 모든 엔드포인트에서 Null 방지를 공통 처리한다.
     * 코드 의미: `Objects.requireNonNull`로 인증 정보와 사용자 ID를 순차적으로 확인한다.
     * 기대 결과: ID가 없을 경우 즉시 예외가 발생해 잘못된 요청을 차단한다.
     */
    private UUID requireUserId(CustomUserDetails user) {
        UUID userId = Objects.requireNonNull(user, "인증 정보가 필요합니다.").getUserId();
        return Objects.requireNonNull(userId, "사용자 ID가 필요합니다.");
    }
}

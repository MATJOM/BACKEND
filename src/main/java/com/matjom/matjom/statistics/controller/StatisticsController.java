package com.matjom.matjom.statistics.controller;

import com.matjom.matjom.common.response.ApiResponse;
import com.matjom.matjom.statistics.dto.StatsResponseDTO;
import com.matjom.matjom.statistics.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 장소 통계 API 엔드포인트를 제공하는 컨트롤러.
 * 사용 목적: 인증된 클라이언트가 `/api/places/{placeId}/stats` 경로로 통계 데이터를 요청할 수 있게 한다.
 * 코드 의미: 요청 파라미터를 검증한 뒤 서비스 계층을 호출하고, 공통 응답 래퍼에 데이터를 담아 전달한다.
 * 기대 결과: HTTP 200으로 {@link StatsResponseDTO}를 반환하거나, 장소가 없으면 404 예외를 유도한다.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/places/{placeId}/stats")
@Tag(name = "Statistics", description = "장소 통계 API")
@SecurityRequirement(name = "bearerAuth")
public class StatisticsController {

    private final StatisticsService statisticsService;

    /**
     * 클라이언트에 장소 통계를 제공하는 `/stats` 엔드포인트를 처리한다.
     * 사용 목적: 조회 전용이므로 GET 요청을 받고 파라미터는 양수 ID만 허용한다.
     * 코드 의미: 검증된 placeId로 서비스의 `fetchStats`를 호출하고, 표준 응답 포맷으로 감싼다.
     * 기대 결과: 성공 시 200/JSON 응답, 실패 시 공통 예외 처리 흐름에 따라 에러 응답이 내려간다.
     */
    @Operation(summary = "장소 통계 조회", description = "최근 방문/좋아요 집계를 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "장소를 찾을 수 없음")
    })
    @GetMapping
    public ApiResponse<StatsResponseDTO> getStatistics(
            @Parameter(description = "통계를 조회할 장소 ID", required = true)
            @PathVariable @Positive Long placeId) {
        StatsResponseDTO response = statisticsService.fetchStats(placeId);
        return ApiResponse.ok(response);
    }
}

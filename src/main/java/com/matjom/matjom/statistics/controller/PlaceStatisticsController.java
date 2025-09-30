package com.matjom.matjom.statistics.controller;

import com.matjom.matjom.common.response.ApiResponse;
import com.matjom.matjom.statistics.dto.PlaceStatsResponseDTO;
import com.matjom.matjom.statistics.service.PlaceStatisticsService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/places/{placeId}/stats")
public class PlaceStatisticsController {

    private final PlaceStatisticsService statisticsService;

    // 클라이언트에 장소 통계를 제공하는 `/stats` 엔드포인트를 처리한다.
    @GetMapping
    public ApiResponse<PlaceStatsResponseDTO> getPlaceStatistics(@PathVariable @Positive Long placeId) {
        PlaceStatsResponseDTO response = statisticsService.fetchPlaceStats(placeId);
        return ApiResponse.ok(response);
    }
}

package com.matjom.matjom.placefacade.controller;

import com.matjom.matjom.common.response.ApiResponse;
import com.matjom.matjom.placefacade.dto.PlaceDetailResponseDTO;
import com.matjom.matjom.placefacade.service.PlaceDetailFacadeService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/places/{placeId}/detail")
public class PlaceDetailController {

    private final PlaceDetailFacadeService placeDetailFacadeService;

    // 장소 상세 화면에서 필요한 통계·리뷰 묶음 정보를 반환한다.
    @GetMapping
    public ApiResponse<PlaceDetailResponseDTO> getPlaceDetail(
            @PathVariable @Positive Long placeId,
            @RequestParam(name = "reviewLimit", required = false) Integer reviewLimit
    ) {
        PlaceDetailResponseDTO response = placeDetailFacadeService.getPlaceDetail(placeId, reviewLimit);
        return ApiResponse.ok(response);
    }
}

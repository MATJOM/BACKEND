package com.matjom.matjom.place.controller;

import com.matjom.matjom.common.response.ApiResponse;
import com.matjom.matjom.place.dto.PlaceDetailResponseDTO;
import com.matjom.matjom.place.service.PlaceDetailService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/places")
@RequiredArgsConstructor
public class PlaceController {

    private final PlaceDetailService placeDetailService;

    @GetMapping("/{placeId}")
    public ApiResponse<PlaceDetailResponseDTO> getPlaceDetail(
            @PathVariable @Positive Long placeId,
            @RequestParam(name = "reviewLimit", required = false) Integer reviewLimit
    ) {
        return ApiResponse.ok(placeDetailService.getPlaceDetail(placeId, reviewLimit));
    }
}

package com.matjom.matjom.place.api;

import com.matjom.matjom.common.response.ApiResponse;
import com.matjom.matjom.place.dto.PlaceSearchRequest;
import com.matjom.matjom.place.dto.PlaceSearchResponse;
import com.matjom.matjom.place.service.PlaceSearchService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/places")
@Validated
@RequiredArgsConstructor
public class PlaceController {

    private final PlaceSearchService placeSearchService;

    @GetMapping
    public ApiResponse<PlaceSearchResponse> getPlaces(@Valid @ModelAttribute PlaceSearchRequest request) {
        PlaceSearchResponse response = placeSearchService.search(request);
        return ApiResponse.ok(response);
    }
}

package com.matjom.matjom.place.api;

import com.matjom.matjom.common.response.ApiResponse;
import com.matjom.matjom.feed.dto.response.ReviewResponseDTO;
import com.matjom.matjom.place.dto.PlaceDetailResponseDTO;
import com.matjom.matjom.place.dto.PlaceInfoDTO;
import com.matjom.matjom.place.dto.PlaceSearchRequest;
import com.matjom.matjom.place.dto.PlaceSearchResponse;
import com.matjom.matjom.place.service.PlaceDetailService;
import com.matjom.matjom.place.service.PlaceSearchService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * 장소 검색/상세/리뷰를 제공하는 REST 컨트롤러.
 * 사용 목적: 클라이언트가 단일 엔드포인트 묶음으로 목록과 상세 정보를 조회할 수 있게 한다.
 * 코드 의미: 검색과 상세 서비스로 위임하며, 응답은 공통 `ApiResponse` 포맷으로 감싼다.
 */
@RestController
@RequestMapping("/api/v1/places")
@Validated
@RequiredArgsConstructor
public class PlaceController {
    private final PlaceSearchService placeSearchService;
    private final PlaceDetailService placeDetailService;

    /**
     * 근처 장소 목록을 검색한다.
     * 사용 목적: 위경도/반경/커서를 받아 거리 기반 페이지네이션 목록을 제공한다.
     */
    @GetMapping
    public ApiResponse<PlaceSearchResponse> getPlaces(@Valid @ModelAttribute PlaceSearchRequest request) {
        PlaceSearchResponse response = placeSearchService.search(request);
        return ApiResponse.ok(response);
    }

    /**
     * 장소 상세(정보 + 통계 + 리뷰 요약)를 조회한다.
     * 사용 목적: 리뷰 개수 제한을 조절하며 상세 화면 데이터를 한 번에 반환한다.
     */
    @GetMapping("/{placeId}")
    public ApiResponse<PlaceDetailResponseDTO> getPlaceDetail(
            @PathVariable @Positive Long placeId,
            @RequestParam(name = "reviewLimit", required = false) Integer reviewLimit
    ) {
        return ApiResponse.ok(placeDetailService.getPlaceDetail(placeId, reviewLimit));
    }

    /**
     * 장소 기본 정보만 조회한다.
     * 사용 목적: 통계/리뷰 없이 헤더 데이터만 필요할 때 사용한다.
     */
    @GetMapping("/{placeId}/reviews")
    public ApiResponse<PlaceInfoDTO> getPlaceInfo(
            @PathVariable @Positive Long placeId
    ) {
        return ApiResponse.ok(placeDetailService.getPlaceInfo(placeId));
    }

    /**
     * 장소 리뷰 목록을 조회한다.
     * 사용 목적: 전체 또는 최신 N개의 리뷰만 따로 요청할 때 사용한다.
     */
    @GetMapping("/{placeId}/reviews")
    public ApiResponse<List<ReviewResponseDTO>> getPlaceReviews(
            @PathVariable @Positive Long placeId,
            @RequestParam(name = "limit", required = false) Integer limit
    ) {
        return ApiResponse.ok(placeDetailService.getPlaceReviews(placeId, limit));
    }
}

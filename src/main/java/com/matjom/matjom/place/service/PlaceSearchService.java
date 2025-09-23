package com.matjom.matjom.place.service;

import com.matjom.matjom.place.dto.PlaceSearchRequest;
import com.matjom.matjom.place.dto.PlaceSearchResponse;
import org.springframework.stereotype.Service;

@Service
public class PlaceSearchService {

    public PlaceSearchResponse search(PlaceSearchRequest request) {
        // TODO: 2.2 이후 실제 검색 구현 (캐시, PostGIS 질의)
        return PlaceSearchResponse.empty();
    }
}

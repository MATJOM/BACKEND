package com.matjom.matjom.recommendation.service;

import com.matjom.matjom.recommendation.dto.RouletteRequest;
import com.matjom.matjom.recommendation.dto.RouletteResponse;
import org.springframework.stereotype.Service;

@Service
public class RouletteService {

    public RouletteResponse recommend(RouletteRequest request, String idempotencyKey) {
        // TODO: 3.2 단계에서 후보군 조회 및 균등 추천 로직 구현
        return RouletteResponse.placeholder();
    }
}

package com.matjom.matjom.recommendation.dto;

import java.util.List;

public record RouletteResponse(Long placeId,
                               String name,
                               double distanceMeters,
                               List<String> categories,
                               double latitude,
                               double longitude,
                               Meta meta) {

    public record Meta(int candidateCount, boolean replayed) {
        // 후보 수와 멱등 재생 여부
    }
}

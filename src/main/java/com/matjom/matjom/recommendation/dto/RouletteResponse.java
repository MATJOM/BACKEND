package com.matjom.matjom.recommendation.dto;

import java.util.List;

public record RouletteResponse(Long placeId,
                               String name,
                               double distanceMeters,
                               List<String> categories,
                               Meta meta) {

    public record Meta(int candidateCount, boolean replayed) {
    }
}

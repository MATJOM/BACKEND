package com.matjom.matjom.recommendation.dto;

import java.util.List;

public record RouletteResponse(Long placeId,
                               String name,
                               double distanceMeters,
                               List<String> categories,
                               Meta meta) {

    public static RouletteResponse placeholder() {
        return new RouletteResponse(null, null, 0.0, List.of(), new Meta(0, false));
    }

    public record Meta(int candidateCount, boolean replayed) {
    }
}

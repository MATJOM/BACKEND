package com.matjom.matjom.recommendation.dto;

public record RouletteResponse(Long placeId, String name) {

    public static RouletteResponse placeholder() {
        return new RouletteResponse(null, null);
    }
}

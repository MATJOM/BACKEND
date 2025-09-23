package com.matjom.matjom.place.dto;

import java.util.Collections;
import java.util.List;

public record PlaceSearchResponse(List<PlaceSummary> places, String nextCursor) {

    public static PlaceSearchResponse empty() {
        return new PlaceSearchResponse(Collections.emptyList(), null);
    }

    public record PlaceSummary(Long placeId, String name, double distanceMeters) {
    }
}

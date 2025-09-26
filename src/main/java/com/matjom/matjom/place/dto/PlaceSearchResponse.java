package com.matjom.matjom.place.dto;

import java.util.Collections;
import java.util.List;

public record PlaceSearchResponse(List<PlaceSummary> places,
                                  String nextCursor,
                                  Meta meta) {

    public PlaceSearchResponse(List<PlaceSummary> places, String nextCursor) {
        this(places, nextCursor, null);
    }

    public static PlaceSearchResponse empty() {
        return new PlaceSearchResponse(Collections.emptyList(), null, null);
    }

    public record PlaceSummary(Long placeId, String name, double distanceMeters) {
    }

    public record Meta(String reason, String suggest) {
    }
}

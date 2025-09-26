package com.matjom.matjom.place.dto;

import java.util.List;

public record PlaceSearchResponse(List<PlaceSummary> places,
                                  String nextCursor,
                                  Meta meta) {

    public PlaceSearchResponse(List<PlaceSummary> places, String nextCursor) {
        this(places, nextCursor, null);
    }

    public record PlaceSummary(Long placeId, String name, double distanceMeters) {
    }

    public record Meta(String reason, String suggest) {
    }
}

package com.matjom.matjom.recommendation.dto;

import java.util.List;

public record RouletteCandidate(Long placeId,
                                String name,
                                double distanceMeters,
                                List<String> categories,
                                double latitude,
                                double longitude) {
} // Repository → Service 전달 객체

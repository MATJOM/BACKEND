package com.matjom.matjom.recommendation.service;

import com.matjom.matjom.common.exception.base.RecommendationException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.place.repository.PlaceRepository;
import com.matjom.matjom.recommendation.dto.RouletteCandidate;
import com.matjom.matjom.recommendation.dto.RouletteRequest;
import com.matjom.matjom.recommendation.dto.RouletteResponse;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;

@Service
public class RouletteService {

    private static final double DEFAULT_RADIUS_METERS = 300.0;
    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT = 500;

    private final PlaceRepository placeRepository;

    public RouletteService(PlaceRepository placeRepository) {
        this.placeRepository = placeRepository;
    }

    public RouletteResponse recommend(RouletteRequest request, String idempotencyKey) {
        double radius = request.radiusOrDefault(DEFAULT_RADIUS_METERS);
        int limit = Math.min(request.limitOrDefault(DEFAULT_LIMIT), MAX_LIMIT);

        List<RouletteCandidate> candidates = placeRepository.findRouletteCandidates(
                request.getLat(),
                request.getLng(),
                radius,
                request.categoriesOrNull(),
                limit);

        if (candidates.isEmpty()) {
            throw new RecommendationException(ErrorCode.ROULETTE_NO_CANDIDATE);
        }

        int index = selectIndex(candidates.size(), request.getSeed());
        RouletteCandidate chosen = candidates.get(index);

        return new RouletteResponse(
                chosen.placeId(),
                chosen.name(),
                chosen.distanceMeters(),
                chosen.categories(),
                new RouletteResponse.Meta(candidates.size(), false)
        );
    }

    private int selectIndex(int size, Long seed) {
        if (size <= 1) {
            return 0;
        }
        if (seed == null) {
            return ThreadLocalRandom.current().nextInt(size);
        }
        return new Random(seed).nextInt(size);
    }
}

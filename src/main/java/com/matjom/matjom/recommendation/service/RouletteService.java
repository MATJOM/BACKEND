package com.matjom.matjom.recommendation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matjom.matjom.common.exception.base.IdempotencyException;
import com.matjom.matjom.common.exception.base.RecommendationException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.common.idempotency.IdempotencyCallback;
import com.matjom.matjom.common.idempotency.IdempotencyResult;
import com.matjom.matjom.common.idempotency.IdempotencyStore;
import com.matjom.matjom.place.repository.PlaceRepository;
import com.matjom.matjom.recommendation.dto.RouletteCandidate;
import com.matjom.matjom.recommendation.dto.RouletteRequest;
import com.matjom.matjom.recommendation.dto.RouletteResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;

@Service
public class RouletteService {

    private static final double DEFAULT_RADIUS_METERS = 300.0;
    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT = 500;
    private static final String IDEMPOTENCY_PREFIX = "idemp:roulette:";

    private final PlaceRepository placeRepository;
    private final IdempotencyStore idempotencyStore;
    private final ObjectMapper objectMapper;

    public RouletteService(PlaceRepository placeRepository,
                           IdempotencyStore idempotencyStore,
                           ObjectMapper objectMapper) {
        this.placeRepository = placeRepository;
        this.idempotencyStore = idempotencyStore;
        this.objectMapper = objectMapper;
    }

    public RouletteResponse recommend(final RouletteRequest request, String idempotencyKey) {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        String redisKey = IDEMPOTENCY_PREFIX + idempotencyKey;
        String requestHash = computeRequestHash(request);

        IdempotencyResult<RouletteResponse> result = idempotencyStore.replayOrRun(
                redisKey,
                requestHash,
                RouletteResponse.class,
                new IdempotencyCallback<RouletteResponse>() {
                    @Override
                    public RouletteResponse execute() {
                        return executeRecommendation(request);
                    }
                }
        );

        if (result.isReplayed()) {
            return markReplayed(result.getValue());
        }
        return result.getValue();
    }

    private RouletteResponse executeRecommendation(RouletteRequest request) {
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

    private RouletteResponse markReplayed(RouletteResponse original) {
        RouletteResponse.Meta meta = original.meta();
        int candidateCount = meta == null ? 0 : meta.candidateCount();
        return new RouletteResponse(
                original.placeId(),
                original.name(),
                original.distanceMeters(),
                original.categories(),
                new RouletteResponse.Meta(candidateCount, true)
        );
    }

    private String computeRequestHash(RouletteRequest request) {
        byte[] jsonBytes = toJsonBytes(request);
        MessageDigest digest = messageDigest();
        byte[] hashed = digest.digest(jsonBytes);
        StringBuilder builder = new StringBuilder(hashed.length * 2);
        for (byte value : hashed) {
            int unsigned = value & 0xFF;
            String hex = Integer.toHexString(unsigned);
            if (hex.length() == 1) {
                builder.append('0');
            }
            builder.append(hex);
        }
        return builder.toString();
    }

    private byte[] toJsonBytes(RouletteRequest request) {
        try {
            return objectMapper.writeValueAsBytes(request);
        } catch (JsonProcessingException ex) {
            throw new IdempotencyException(ErrorCode.INTERNAL_SERVER_ERROR, "요청 직렬화에 실패했습니다.");
        }
    }

    private MessageDigest messageDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 해시 함수를 사용할 수 없습니다.", ex);
        }
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

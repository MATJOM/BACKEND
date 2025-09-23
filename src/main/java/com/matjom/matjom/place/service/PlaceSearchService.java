package com.matjom.matjom.place.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matjom.matjom.place.dto.PlaceSearchRequest;
import com.matjom.matjom.place.dto.PlaceSearchResponse;
import com.matjom.matjom.place.dto.PlaceSearchResponse.PlaceSummary;
import com.matjom.matjom.place.repository.PlaceRepository;
import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PlaceSearchService {

    private static final double DEFAULT_RADIUS_METERS = 300.0;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final Duration CACHE_TTL = Duration.ofSeconds(60);

    private final PlaceRepository placeRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public PlaceSearchService(PlaceRepository placeRepository,
                              StringRedisTemplate redisTemplate,
                              ObjectMapper objectMapper) {
        this.placeRepository = placeRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public PlaceSearchResponse search(PlaceSearchRequest request) {
        double lat = request.getLat();
        double lng = request.getLng();
        double radius = request.getRadius() != null ? request.getRadius() : DEFAULT_RADIUS_METERS;
        int size = request.getSize() != null ? request.getSize() : DEFAULT_PAGE_SIZE;

        String cacheKey = buildCacheKey(lat, lng, radius, size, request.getCursor(), request.getFilters());
        ValueOperations<String, String> ops = redisTemplate.opsForValue();

        PlaceSearchResponse cached = readCache(ops, cacheKey);
        if (cached != null) {
            return cached;
        }

        List<PlaceSummary> summaries = placeRepository.search(lat, lng, radius, size, request.getFilters());
        PlaceSearchResponse response = new PlaceSearchResponse(summaries, null);

        writeCache(ops, cacheKey, response);
        return response;
    }

    private PlaceSearchResponse readCache(ValueOperations<String, String> ops, String cacheKey) {
        String cachedJson = ops.get(cacheKey);
        if (!StringUtils.hasText(cachedJson)) {
            return null;
        }
        try {
            return objectMapper.readValue(cachedJson, PlaceSearchResponse.class);
        } catch (JsonProcessingException e) {
            // 캐시 데이터가 손상된 경우 삭제하고 캐시 미스 처리
            redisTemplate.delete(cacheKey);
            return null;
        }
    }

    private void writeCache(ValueOperations<String, String> ops, String cacheKey, PlaceSearchResponse response) {
        try {
            ops.set(cacheKey, objectMapper.writeValueAsString(response), CACHE_TTL);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("검색 결과 직렬화에 실패했습니다.", e);
        }
    }

    private String buildCacheKey(double lat, double lng, double radius, int size, String cursor, String filters) {
        return new StringBuilder("place:search:")
                .append(String.format("lat=%.6f:", lat))
                .append(String.format("lng=%.6f:", lng))
                .append(String.format("radius=%.1f:", radius))
                .append("size=").append(size).append(':')
                .append("cursor=").append(cursor == null ? "" : cursor).append(':')
                .append("filters=").append(filters == null ? "" : filters.trim())
                .toString();
    }
}

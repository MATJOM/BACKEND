package com.matjom.matjom.place.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matjom.matjom.place.dto.PlaceSearchCursor;
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
    private static final int MAX_RESULTS_PER_SEARCH = 500;
    private static final int MAX_FETCH_LIMIT = MAX_RESULTS_PER_SEARCH + 1;
    private static final Duration CACHE_TTL = Duration.ofSeconds(60);
    private static final int LOW_RESULTS_SUGGEST_THRESHOLD = 20;

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
        double radius = request.radiusOrDefault(DEFAULT_RADIUS_METERS);
        int requestedSize = request.sizeOrDefault(DEFAULT_PAGE_SIZE);
        int pageSize = Math.min(requestedSize, MAX_RESULTS_PER_SEARCH);
        PlaceSearchCursor cursorToken = request.parseCursor().orElse(null);

        String cacheKey = buildCacheKey(lat, lng, radius, requestedSize, request.getCursor(), request.getFilters());
        ValueOperations<String, String> ops = redisTemplate.opsForValue();

        PlaceSearchResponse cached = readCache(ops, cacheKey);
        if (cached != null) {
            return cached;
        }

        int fetchLimit = pageSize == MAX_RESULTS_PER_SEARCH
                ? MAX_RESULTS_PER_SEARCH + 1
                : pageSize + 1;
        List<PlaceSummary> fetchedSummaries = placeRepository.search(lat, lng, radius, fetchLimit, cursorToken, request.getFilters());

        boolean exceedsMaxResults = fetchedSummaries.size() > MAX_RESULTS_PER_SEARCH;
        String nextCursor = buildNextCursor(fetchedSummaries, pageSize);

        List<PlaceSummary> pageSummaries = trimToPage(fetchedSummaries, pageSize);
        PlaceSearchResponse.Meta meta;
        if (exceedsMaxResults) {
            meta = new PlaceSearchResponse.Meta("too_many_results", "검색 반경을 줄이거나 필터를 추가해 주세요.");
        } else if (pageSummaries.size() < LOW_RESULTS_SUGGEST_THRESHOLD) {
            meta = new PlaceSearchResponse.Meta("low_results", "검색 결과가 적습니다. 반경을 늘리거나 필터를 완화해 보세요.");
        } else {
            meta = null;
        }
        PlaceSearchResponse response = new PlaceSearchResponse(pageSummaries, nextCursor, meta);

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
        return "place:search:" +
                String.format("lat=%.6f:", lat) +
                String.format("lng=%.6f:", lng) +
                String.format("radius=%.1f:", radius) +
                "size=" + size + ':' +
                "cursor=" + (cursor == null ? "" : cursor) + ':' +
                "filters=" + (filters == null ? "" : filters.trim());
    }

    private String buildNextCursor(List<PlaceSummary> summaries, int pageSize) {
        if (pageSize <= 0 || summaries.size() <= pageSize) {
            return null;
        }
        PlaceSummary last = summaries.get(pageSize - 1);
        return PlaceSearchCursor.toToken(last.distanceMeters(), last.placeId());
    }

    private List<PlaceSummary> trimToPage(List<PlaceSummary> summaries, int pageSize) {
        if (pageSize <= 0) {
            return List.of();
        }
        if (summaries.size() <= pageSize) {
            return List.copyOf(summaries);
        }
        return List.copyOf(summaries.subList(0, pageSize));
    }
}

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

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PlaceSearchService {
	// 반경 입력이 없을 때 300m 기본값으로 “근처 탐색” 경험을 유지합니다.
    private static final double DEFAULT_RADIUS_METERS = 300.0;

	// 페이지 크기 기본값을 20으로 고정해 캐시/커서 기준점을 안정화합니다.
    private static final int DEFAULT_PAGE_SIZE = 20;

	// 500건 상한. pageSize 계산과 초과 시 meta.reason="too_many_results" 안내에 사용됩니다.
    private static final int MAX_RESULTS_PER_SEARCH = 500;

	// MAX_RESULTS_PER_SEARCH + 1을 담는 상수로, 501건 조회로 상한 초과 여부를 감지하려는 의도입니다. 현재 구현은 직접 + 1을 사용하고 있어 미사용 상태입니다.
	private static final int MAX_FETCH_LIMIT = MAX_RESULTS_PER_SEARCH + 1;

	// Redis 캐시 TTL 60초. writeCache에서 ops.set(…, CACHE_TTL)로 적용됩니다
	private static final Duration CACHE_TTL = Duration.ofSeconds(60);

	// 결과가 20건 미만일 때 meta.reason="low_results" 안내를 내려 반경 확대를 유도합니다.
	private static final int LOW_RESULTS_SUGGEST_THRESHOLD = 20;

    private final PlaceRepository placeRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public PlaceSearchResponse search(PlaceSearchRequest request) {
        double lat = request.getLat(); //컨트롤러 검증을 통과한 위도 → PostGIS 쿼리 기준점
        double lng = request.getLng(); //경도 역시 그대로 거리 계산에 활용
        double radius = request.radiusOrDefault(DEFAULT_RADIUS_METERS); // 입력이 없으면 300m 기본값 적용
        int requestedSize = request.sizeOrDefault(DEFAULT_PAGE_SIZE); // 클라이언트 요청 페이지 크기(없으면 20)
        int pageSize = Math.min(requestedSize, MAX_RESULTS_PER_SEARCH); //500개 상한으로 서버 부담을 제어
        PlaceSearchCursor cursorToken = request.parseCursor().orElse(null); // 커서 문자열을 DTO가 파싱해 Optional로 전달

        String cacheKey = buildCacheKey(lat, lng, radius, requestedSize, request.getCursor(), request.getFilters()); // 요청 파라미터 전체를 포함한 캐시 키 → 결과 정합성 유지
        ValueOperations<String, String> ops = redisTemplate.opsForValue(); // Redis 문자열 연산 핸들 (get/set)

        PlaceSearchResponse cached = readCache(ops, cacheKey); // 캐시에서 직전 응답을 조회, 손상 시 삭제
        if (cached != null) {
            return cached; // 캐시 히트면 DB 접근 없이 즉시 반환
        }
        int fetchLimit = pageSize == MAX_RESULTS_PER_SEARCH
                ? MAX_RESULTS_PER_SEARCH + 1 // 500 요청 시 501번째까지 조회해 상한 초과 여부 확인
                : pageSize + 1; // 일반 페이지도 +1로 조회해 다음 커서 존재 여부 판단
        List<PlaceSummary> fetchedSummaries = placeRepository.search(lat, lng, radius, fetchLimit, cursorToken, request.getFilters()); // PostGIS Native SQL 실행 (filters는 후속 과제)

        boolean exceedsMaxResults = fetchedSummaries.size() > MAX_RESULTS_PER_SEARCH; // 501건 이상이면 too_many_results 메타 생성
        String nextCursor = buildNextCursor(fetchedSummaries, pageSize); // pageSize+1 결과에서 다음 커서 생성

        List<PlaceSummary> pageSummaries = trimToPage(fetchedSummaries, pageSize); // 실제 응답 크기로 리스트 슬라이스
        PlaceSearchResponse.Meta meta;
        if (exceedsMaxResults) {
            meta = new PlaceSearchResponse.Meta("too_many_results", "검색 반경을 줄이거나 필터를 추가해 주세요."); // 상한 초과 안내 문구
        } else if (pageSummaries.size() < LOW_RESULTS_SUGGEST_THRESHOLD) {
            meta = new PlaceSearchResponse.Meta("low_results", "검색 결과가 적습니다. 반경을 늘리거나 필터를 완화해 보세요."); // 결과가 적을 때 UX 가이드 제공
        } else {
            meta = null; // 특별 안내가 필요 없는 경우 메타 생략
        }
        PlaceSearchResponse response = new PlaceSearchResponse(pageSummaries, nextCursor, meta); // 본문/커서/메타를 묶어 응답 객체 생성

        writeCache(ops, cacheKey, response); // 동일 조건 재요청 대비 60초 캐싱
        return response; // 최종 응답 반환
    }

    private PlaceSearchResponse readCache(ValueOperations<String, String> ops, String cacheKey) {
        String cachedJson = ops.get(cacheKey);   // 1) Redis에서 문자열(JSON) 조회
        if (!StringUtils.hasText(cachedJson)) {  // 2) null/빈문자면 캐시 미스
            return null;
        }
        try {
            return objectMapper.readValue(cachedJson, PlaceSearchResponse.class);   // 3) JSON→DTO
        } catch (JsonProcessingException e) {
            redisTemplate.delete(cacheKey);  // 캐시 데이터가 손상된 경우 삭제하고 캐시 미스 처리
            return null;  // 그리고 캐시 미스 처리
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

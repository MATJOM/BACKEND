package com.matjom.matjom.statistics.service;

import com.matjom.matjom.common.exception.base.PlaceException;
import com.matjom.matjom.statistics.cache.PlaceStatsCacheService;
import com.matjom.matjom.statistics.dto.PlaceStatsResponseDTO;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceStatisticsQueryService {

    private final PlaceStatsCacheService cacheService;
    private final PlaceStatisticsService placeStatisticsService;

    // Cache-Aside 패턴으로 Redis를 먼저 조회하고 미스가 나면 DB에서 값을 갱신한다.
    public PlaceStatsResponseDTO getPlaceStats(Long placeId) {
        Optional<PlaceStatsResponseDTO> cached = cacheService.get(placeId);
        try {
            PlaceStatsResponseDTO fresh = placeStatisticsService.fetchPlaceStats(placeId);
            cacheService.put(placeId, fresh); // 9월 29일 최종: 캐시 키는 placeId, 응답은 placeName 포함
            log.info("place stats refreshed: placeId={}, source={}, ttlSeconds={}",
                    placeId,
                    fresh.getDataSource(),
                    fresh.getCacheTtlSeconds());
            return fresh;
        } catch (PlaceException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            if (cached.isPresent()) {
                PlaceStatsResponseDTO fallback = cached.get();
                log.warn("Failed to refresh place stats from DB, falling back to cache: placeId={}, cachedSource={}, cachedTtlSeconds={}",
                        placeId,
                        fallback.getDataSource(),
                        fallback.getCacheTtlSeconds(),
                        ex);
                return fallback;
            }
            throw ex;
        }
    }

    // 다음 요청에서 DB를 타도록 캐시를 명시적으로 비운다.
    public void evictCache(Long placeId) {
        cacheService.evict(placeId);
    }
}

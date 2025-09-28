package com.matjom.matjom.statistics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.matjom.matjom.statistics.cache.PlaceStatsCacheService;
import com.matjom.matjom.statistics.dto.PlaceStatsResponseDTO;
import com.matjom.matjom.statistics.dto.PlaceStatsSnapshot;
import com.matjom.matjom.statistics.dto.StatsDataSource;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlaceStatisticsQueryServiceTest {

    @Mock
    private PlaceStatsCacheService cacheService;

    @Mock
    private PlaceStatisticsService placeStatisticsService;

    private PlaceStatisticsQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new PlaceStatisticsQueryService(cacheService, placeStatisticsService);
    }

    @Test
    // 캐시가 있어도 DB 갱신 후 캐시를 덮어쓰는지 확인한다.
    void returnsFreshStatisticsAndUpdatesCacheEvenWhenCacheExists() {
        PlaceStatsResponseDTO cached = PlaceStatsResponseDTO.of(
                "테스트 장소 15",
                new PlaceStatsSnapshot(100L, 40L, 5L, 3L),
                OffsetDateTime.parse("2024-09-26T00:00:00Z"),
                300L,
                StatsDataSource.CACHE);
        PlaceStatsResponseDTO fresh = PlaceStatsResponseDTO.of(
                "테스트 장소 15",
                new PlaceStatsSnapshot(120L, 45L, 8L, 5L),
                OffsetDateTime.parse("2024-09-26T00:01:00Z"),
                300L,
                StatsDataSource.DATABASE);
        when(cacheService.get(15L)).thenReturn(Optional.of(cached));
        when(placeStatisticsService.fetchPlaceStats(15L)).thenReturn(fresh);

        PlaceStatsResponseDTO result = queryService.getPlaceStats(15L);

        assertThat(result).isSameAs(fresh);
        verify(placeStatisticsService).fetchPlaceStats(15L);
        verify(cacheService).put(15L, fresh);
    }

    @Test
    // 캐시 미스 상황에서 DB 조회와 캐시 저장이 모두 수행되는지 검증한다.
    void fetchesFromDatabaseAndCachesWhenMiss() {
        PlaceStatsResponseDTO fresh = PlaceStatsResponseDTO.of(
                "테스트 장소 21",
                new PlaceStatsSnapshot(90L, 30L, 4L, 2L),
                OffsetDateTime.parse("2024-09-26T01:00:00Z"),
                300L,
                StatsDataSource.DATABASE);
        when(cacheService.get(21L)).thenReturn(Optional.empty());
        when(placeStatisticsService.fetchPlaceStats(21L)).thenReturn(fresh);

        PlaceStatsResponseDTO result = queryService.getPlaceStats(21L);

        assertThat(result).isSameAs(fresh);
        verify(placeStatisticsService).fetchPlaceStats(21L);
        verify(cacheService).put(21L, fresh);
    }

    @Test
    // DB 오류 시 캐시된 값을 안전하게 반환하는지 확인한다.
    void fallsBackToCachedValueWhenDatabaseFails() {
        PlaceStatsResponseDTO cached = PlaceStatsResponseDTO.of(
                "테스트 장소 30",
                new PlaceStatsSnapshot(70L, 25L, 3L, 2L),
                OffsetDateTime.parse("2024-09-26T02:00:00Z"),
                300L,
                StatsDataSource.CACHE);
        when(cacheService.get(30L)).thenReturn(Optional.of(cached));
        when(placeStatisticsService.fetchPlaceStats(30L)).thenThrow(new RuntimeException("DB down"));

        PlaceStatsResponseDTO result = queryService.getPlaceStats(30L);

        assertThat(result).isSameAs(cached);
        verify(placeStatisticsService).fetchPlaceStats(30L);
        verify(cacheService, never()).put(anyLong(), any());
    }

    @Test
    // 캐시가 없고 DB도 실패하면 예외가 그대로 전파되는지 검증한다.
    void throwsExceptionWhenDatabaseFailsAndNoCache() {
        when(cacheService.get(44L)).thenReturn(Optional.empty());
        when(placeStatisticsService.fetchPlaceStats(44L)).thenThrow(new RuntimeException("DB down"));

        assertThatThrownBy(() -> queryService.getPlaceStats(44L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB down");

        verify(cacheService, never()).put(anyLong(), any());
    }
}

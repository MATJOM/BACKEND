package com.matjom.matjom.statistics.service;

import com.matjom.matjom.common.exception.base.PlaceException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.feed.repository.PlaceReadRepository;
import com.matjom.matjom.statistics.dto.PlaceStatsResponseDTO;
import com.matjom.matjom.statistics.dto.PlaceStatsSnapshot;
import com.matjom.matjom.statistics.dto.StatsDataSource;
import com.matjom.matjom.statistics.repository.PlaceStatisticsRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlaceStatisticsService {

    private static final ZoneId STATISTICS_ZONE_ID = ZoneId.of("Asia/Seoul"); // 9월 26일 최종: 일자 계산 기준 KST

    private final PlaceReadRepository placeReadRepository;
    private final PlaceStatisticsRepository placeStatisticsRepository;
    private final Clock clock;
    private final long cacheTtlSeconds;

    public PlaceStatisticsService(PlaceReadRepository placeReadRepository,
                                  PlaceStatisticsRepository placeStatisticsRepository,
                                  Clock clock,
                                  @Value("${statistics.cache.ttl-seconds:300}") long cacheTtlSeconds) {
        this.placeReadRepository = placeReadRepository;
        this.placeStatisticsRepository = placeStatisticsRepository;
        this.clock = clock;
        this.cacheTtlSeconds = cacheTtlSeconds;
    }

    // 장소 존재 여부를 검증하고 최신 통계 스냅샷을 조회한 뒤 DTO로 만들어 반환한다.
    @Transactional(readOnly = true)
    public PlaceStatsResponseDTO fetchPlaceStats(Long placeId) {
        String placeName = placeReadRepository.findNameById(placeId)
                .orElseThrow(() -> new PlaceException(ErrorCode.PLACE_NOT_FOUND)); // 9월 29일 최종: 이름 확보와 존재 검증 동시 처리

        OffsetDateTime now = OffsetDateTime.now(clock); // 9월 26일 최종: 공통 Clock 주입
        PlaceStatsSnapshot snapshot = placeStatisticsRepository.fetchSnapshot(
                placeId,
                now.atZoneSameInstant(STATISTICS_ZONE_ID).toLocalDate()
        );

        return PlaceStatsResponseDTO.of(placeName, snapshot, now, cacheTtlSeconds, StatsDataSource.DATABASE); // 9월 29일 최종: 사용자에게 장소 이름 제공
    }
}

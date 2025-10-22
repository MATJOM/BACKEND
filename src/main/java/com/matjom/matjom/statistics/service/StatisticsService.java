package com.matjom.matjom.statistics.service;

import com.matjom.matjom.common.exception.base.PlaceException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.place.repository.PlaceReadRepository;
import com.matjom.matjom.statistics.dto.StatsResponseDTO;
import com.matjom.matjom.statistics.dto.StatsSnapshot;
import com.matjom.matjom.statistics.repository.StatisticsRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장소 통계 비즈니스 로직을 담당하는 서비스 계층.
 * 사용 목적: 컨트롤러 요청을 받아 장소 존재 여부를 확인하고 통계 데이터를 조합한다.
 * 코드 의미: 장소 이름 조회, 통계 저장소 호출, DTO 변환 흐름을 하나의 트랜잭션으로 묶는다.
 * 기대 결과: `/stats` API가 요청한 장소 ID에 대한 최신 통계를 안전하게 반환한다.
 */
@Service
public class StatisticsService {

    private static final ZoneId STATISTICS_ZONE_ID = ZoneId.of("Asia/Seoul"); // 통계 일자 계산 기준 타임존(KST)

    private final PlaceReadRepository placeReadRepository;
    private final StatisticsRepository statisticsRepository;
    private final Clock clock;

    public StatisticsService(PlaceReadRepository placeReadRepository,
                             StatisticsRepository statisticsRepository,
                             Clock clock) {
        this.placeReadRepository = placeReadRepository;
        this.statisticsRepository = statisticsRepository;
        this.clock = clock;
    }

    /**
     * 장소 존재 여부를 검증하고 최신 통계 스냅샷을 조회한 뒤 DTO로 만들어 반환한다.
     * 사용 목적: 유효한 장소에 대해서만 통계 조회를 허용하고, 누락 시 명확한 예외를 던진다.
     * 코드 의미: 장소 이름 조회 → 기준일 산출 → 통계 스냅샷 조회 → 응답 DTO 변환 순으로 처리한다.
     * 기대 결과: 장소 이름과 누적/시간대 통계를 포함한 {@link StatsResponseDTO}를 반환한다.
     */
    @Transactional(readOnly = true)
    public StatsResponseDTO fetchStats(Long placeId) {
        String placeName = placeReadRepository.findNameById(placeId)
                .orElseThrow(() -> new PlaceException(ErrorCode.PLACE_NOT_FOUND)); // 9월 29일 최종: 이름 확보와 존재 검증 동시 처리

        OffsetDateTime now = OffsetDateTime.now(clock); // 9월 26일 최종: 공통 Clock 주입
        StatsSnapshot snapshot = statisticsRepository.fetchSnapshot(
                placeId,
                now.atZoneSameInstant(STATISTICS_ZONE_ID).toLocalDate()
        );

        return StatsResponseDTO.of(placeName, snapshot); // 9월 30일 개편: 캐시 없이 DB 스냅샷만 반환
    }
}

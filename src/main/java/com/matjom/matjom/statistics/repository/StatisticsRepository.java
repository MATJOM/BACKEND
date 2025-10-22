package com.matjom.matjom.statistics.repository;

import com.matjom.matjom.statistics.dto.StatsSnapshot;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/**
 * Native-query repository that collects place level visit/like aggregates and hourly arrival averages.
 * <p>
 * 사용 목적: `/stats` API에서 사용할 최신 통계 스냅샷을 DB에서 직접 가져오기 위함.
 * 코드 의미: 통계 집계에 필요한 SQL을 문자열 상수로 정의하고, EntityManager를 통해 실행한다.
 * 기대 결과: 지정된 장소와 기준일에 대한 누적 방문/좋아요 수와 11~20시 시간대별 평균 방문자를 반환한다.
 */

@Repository
public class StatisticsRepository {

    @PersistenceContext
    private EntityManager entityManager;

    private static final int AVERAGE_WINDOW_DAYS = 14;        // 통계 평균을 계산할 기간(최근 14일)
    private static final int AVERAGE_WINDOW_OFFSET = AVERAGE_WINDOW_DAYS - 1;

    private static final String SNAPSHOT_SQL = ("""
        SELECT
            -- 누적 방문자 수: arrived_at이 NULL이 아닌 방문 기록만 카운트
            (SELECT COALESCE(COUNT(*), 0)
             FROM visits v_total
             WHERE v_total.place_id = :placeId
               AND v_total.arrived_at IS NOT NULL) AS total_visitors,
            -- 누적 좋아요 수: ACTIVE 상태의 daily_likes 집계
            (SELECT COALESCE(COUNT(*), 0)
             FROM daily_likes dl
             WHERE dl.place_id = :placeId
               AND dl.status = 'ACTIVE') AS total_likes
        """
    ); // 9월 30일 최종: 누적 방문/좋아요 집계 전용

    private static final String HOURLY_AVERAGE_SQL = ("""
        SELECT hour_key AS hour,
               -- 최근 14일 동안 해당 시간대 방문 수의 평균을 정수로 반올림
               CAST(COALESCE(ROUND(COUNT(v_hour.visit_id)::numeric / %1$d, 0), 0) AS bigint) AS average_arrivals
        FROM generate_series(11, 20) AS hour_key -- 11시부터 20시까지 시간대를 생성
        LEFT JOIN visits v_hour
          ON v_hour.place_id = :placeId
         AND v_hour.arrived_at IS NOT NULL
         AND ((v_hour.arrived_at AT TIME ZONE 'Asia/Seoul')::date BETWEEN :startDate AND :endDate) -- 기준일 포함 14일 범위
         AND EXTRACT(HOUR FROM (v_hour.arrived_at AT TIME ZONE 'Asia/Seoul')) = hour_key -- 시간대 일치 여부
        GROUP BY hour_key
        ORDER BY hour_key
        """
    ).formatted(AVERAGE_WINDOW_DAYS); // 11~20시 시간대별 14일 평균 도착 인원을 계산하는 쿼리

    /**
     * `/stats` 엔드포인트에 제공할 집계 스냅샷을 조회한다.
     * 사용 목적: 서비스 계층이 장소 이름과 함께 전달할 방문/좋아요/시간대별 평균 데이터를 확보한다.
     * 코드 의미: 누적 통계와 시간대 평균 쿼리를 실행해 DTO 형태로 제공할 기초 데이터를 만든다.
     * 기대 결과: 요청한 장소 ID와 기준일에 대한 {@link StatsSnapshot} 인스턴스를 반환한다.
     */
    public StatsSnapshot fetchSnapshot(Long placeId, LocalDate targetDate) {
        Object[] row = (Object[]) entityManager.createNativeQuery(SNAPSHOT_SQL)
                .setParameter("placeId", placeId)
                .getSingleResult();

        long totalVisitors = ((Number) row[0]).longValue();
        long totalLikes = ((Number) row[1]).longValue();
        Map<Integer, Long> hourlyAverages = fetchHourlyAverages(placeId, targetDate);

        return new StatsSnapshot(totalVisitors, totalLikes, hourlyAverages);
    }

    /**
     * 시간대별 평균 방문자 수를 계산한다.
     * 사용 목적: `/stats` 응답에서 차트로 활용할 11~20시 구간의 평균 방문자를 제공한다.
     * 코드 의미: 기준일과 14일 윈도우를 적용한 네이티브 쿼리를 실행하고, 비어 있는 시간대는 기본값 0으로 채운다.
     * 기대 결과: 시간 순서가 보장된 `hour -> 평균 방문자 수` 맵을 반환한다.
     */
    private Map<Integer, Long> fetchHourlyAverages(Long placeId, LocalDate targetDate) {
        LocalDate startDate = targetDate.minusDays(AVERAGE_WINDOW_OFFSET);
        List<Object[]> rows = entityManager.createNativeQuery(HOURLY_AVERAGE_SQL)
                .setParameter("placeId", placeId)
                .setParameter("startDate", startDate)
                .setParameter("endDate", targetDate)
                .getResultList();

        Map<Integer, Long> hourly = new LinkedHashMap<>();
        for (int hour = 11; hour <= 20; hour++) {
            hourly.put(hour, 0L);
        }

        for (Object[] row : rows) {
            if (row == null || row.length < 2) {
                continue;
            }
            Integer hour = ((Number) row[0]).intValue();
            Number average = (Number) row[1];
            hourly.put(hour, average == null ? 0L : average.longValue());
        }

        return hourly;
    }
}

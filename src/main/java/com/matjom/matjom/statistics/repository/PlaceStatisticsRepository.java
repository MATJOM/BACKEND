package com.matjom.matjom.statistics.repository;

import com.matjom.matjom.statistics.dto.PlaceStatsSnapshot;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import org.springframework.stereotype.Repository;

@Repository
public class PlaceStatisticsRepository {

    @PersistenceContext
    private EntityManager entityManager;

    private static final int AVERAGE_WINDOW_DAYS = 14;        // 9월 30일 최종: 최근 14일 평균 계산 기간
    private static final int AVERAGE_WINDOW_OFFSET = AVERAGE_WINDOW_DAYS - 1;

    private static final String SNAPSHOT_SQL = ("""
        SELECT
            (SELECT COALESCE(COUNT(*), 0)
             FROM visits v_total
             WHERE v_total.place_id = :placeId
               AND v_total.arrived_at IS NOT NULL) AS total_visitors,
            (SELECT COALESCE(COUNT(*), 0)
             FROM daily_likes dl
             WHERE dl.place_id = :placeId
               AND dl.status = 'ACTIVE') AS total_likes,
            (SELECT CAST(
                    COALESCE(ROUND(COUNT(*)::numeric / %1$d, 0), 0)
                 AS bigint)
             FROM visits v_1112
             WHERE v_1112.place_id = :placeId
               AND v_1112.arrived_at IS NOT NULL
               AND DATE(v_1112.arrived_at AT TIME ZONE 'Asia/Seoul') BETWEEN (:targetDate - INTERVAL '%2$d day') AND :targetDate
               AND EXTRACT(HOUR FROM (v_1112.arrived_at AT TIME ZONE 'Asia/Seoul')) = 11) AS arrivals_11_12,
            (SELECT CAST(
                    COALESCE(ROUND(COUNT(*)::numeric / %1$d, 0), 0)
                 AS bigint)
             FROM visits v_1213
             WHERE v_1213.place_id = :placeId
               AND v_1213.arrived_at IS NOT NULL
               AND DATE(v_1213.arrived_at AT TIME ZONE 'Asia/Seoul') BETWEEN (:targetDate - INTERVAL '%2$d day') AND :targetDate
               AND EXTRACT(HOUR FROM (v_1213.arrived_at AT TIME ZONE 'Asia/Seoul')) = 12) AS arrivals_12_13
        """
    ).formatted(AVERAGE_WINDOW_DAYS, AVERAGE_WINDOW_OFFSET); // 9월 30일 최종: 특정 시간대 최근 14일 평균

    // `/stats` 엔드포인트에 제공할 집계 스냅샷 쿼리를 실행한다.
    public PlaceStatsSnapshot fetchSnapshot(Long placeId, LocalDate targetDate) {
        Object[] row = (Object[]) entityManager.createNativeQuery(SNAPSHOT_SQL)
                .setParameter("placeId", placeId)
                .setParameter("targetDate", targetDate)
                .getSingleResult();

        long totalVisitors = ((Number) row[0]).longValue();
        long totalLikes = ((Number) row[1]).longValue();
        long arrivals11To12 = ((Number) row[2]).longValue();
        long arrivals12To13 = ((Number) row[3]).longValue();

        return new PlaceStatsSnapshot(totalVisitors, totalLikes, arrivals11To12, arrivals12To13);
    }
}

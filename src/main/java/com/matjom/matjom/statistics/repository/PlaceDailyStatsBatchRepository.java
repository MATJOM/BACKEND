package com.matjom.matjom.statistics.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class PlaceDailyStatsBatchRepository {

    @PersistenceContext
    private EntityManager entityManager;

    private static final String VISIT_STATS_SQL = """
        SELECT v.place_id,
               COUNT(*) FILTER (WHERE DATE(v.started_at AT TIME ZONE 'Asia/Seoul') = :targetDate) AS starts,
               COUNT(*) FILTER (WHERE v.arrived_at IS NOT NULL AND DATE(v.arrived_at AT TIME ZONE 'Asia/Seoul') = :targetDate) AS arrives
        FROM visits v
        WHERE v.deleted_at IS NULL
          AND (
              DATE(v.started_at AT TIME ZONE 'Asia/Seoul') = :targetDate
              OR (v.arrived_at IS NOT NULL AND DATE(v.arrived_at AT TIME ZONE 'Asia/Seoul') = :targetDate)
          )
        GROUP BY v.place_id
    """;

    private static final String HOURLY_STARTS_SQL = """
        SELECT v.place_id,
               CAST(EXTRACT(HOUR FROM (v.started_at AT TIME ZONE 'Asia/Seoul')) AS INT) AS hour,
               COUNT(*)
        FROM visits v
        WHERE v.deleted_at IS NULL
          AND DATE(v.started_at AT TIME ZONE 'Asia/Seoul') = :targetDate
        GROUP BY v.place_id, hour
    """;

    private static final String HOURLY_ARRIVES_SQL = """
        SELECT v.place_id,
               CAST(EXTRACT(HOUR FROM (v.arrived_at AT TIME ZONE 'Asia/Seoul')) AS INT) AS hour,
               COUNT(*)
        FROM visits v
        WHERE v.deleted_at IS NULL
          AND v.arrived_at IS NOT NULL
          AND DATE(v.arrived_at AT TIME ZONE 'Asia/Seoul') = :targetDate
        GROUP BY v.place_id, hour
    """;

    private static final String REVIEW_STATS_SQL = """
        SELECT r.place_id,
               COUNT(*)
        FROM reviews r
        WHERE r.status = 'ACTIVE'
          AND DATE(r.created_at AT TIME ZONE 'Asia/Seoul') = :targetDate
        GROUP BY r.place_id
    """;

    private static final String LIKE_STATS_SQL = """
        SELECT dl.place_id,
               COUNT(*)
        FROM daily_likes dl
        WHERE dl.status = 'ACTIVE'
          AND dl.date_kst = :targetDate
        GROUP BY dl.place_id
    """;

    private static final String UPSERT_SQL = """
        INSERT INTO place_daily_stats (
            date_kst,
            place_id,
            starts,
            arrives,
            reviews,
            likes,
            hourly_arrives,
            hourly_starts,
            peak_hour,
            last_aggregated_at
        ) VALUES (
            :targetDate,
            :placeId,
            :starts,
            :arrives,
            :reviews,
            :likes,
            CAST(:hourlyArrives AS jsonb),
            CAST(:hourlyStarts AS jsonb),
            :peakHour,
            :aggregatedAt
        )
        ON CONFLICT (date_kst, place_id)
        DO UPDATE SET
            starts = EXCLUDED.starts,
            arrives = EXCLUDED.arrives,
            reviews = EXCLUDED.reviews,
            likes = EXCLUDED.likes,
            hourly_arrives = EXCLUDED.hourly_arrives,
            hourly_starts = EXCLUDED.hourly_starts,
            peak_hour = EXCLUDED.peak_hour,
            last_aggregated_at = EXCLUDED.last_aggregated_at,
            updated_at = now()
    """;

    // 대상 일자의 장소별 출발·도착 건수를 조회한다.
    @SuppressWarnings("unchecked")
    public List<Object[]> fetchVisitStats(LocalDate targetDate) {
        return entityManager.createNativeQuery(VISIT_STATS_SQL)
                .setParameter("targetDate", targetDate)
                .getResultList();
    }

    // 시간대별 출발 건수를 조회해 JSON 맵을 구성한다.
    @SuppressWarnings("unchecked")
    public List<Object[]> fetchHourlyStarts(LocalDate targetDate) {
        return entityManager.createNativeQuery(HOURLY_STARTS_SQL)
                .setParameter("targetDate", targetDate)
                .getResultList();
    }

    // 시간대별 도착 건수를 조회해 JSON 맵을 보완한다.
    @SuppressWarnings("unchecked")
    public List<Object[]> fetchHourlyArrives(LocalDate targetDate) {
        return entityManager.createNativeQuery(HOURLY_ARRIVES_SQL)
                .setParameter("targetDate", targetDate)
                .getResultList();
    }

    // 대상 일자에 작성된 활성 리뷰 수를 장소별로 집계한다.
    @SuppressWarnings("unchecked")
    public List<Object[]> fetchReviewStats(LocalDate targetDate) {
        return entityManager.createNativeQuery(REVIEW_STATS_SQL)
                .setParameter("targetDate", targetDate)
                .getResultList();
    }

    // 대상 일자의 활성 좋아요 수를 장소별로 집계한다.
    @SuppressWarnings("unchecked")
    public List<Object[]> fetchLikeStats(LocalDate targetDate) {
        return entityManager.createNativeQuery(LIKE_STATS_SQL)
                .setParameter("targetDate", targetDate)
                .getResultList();
    }

    // 계산된 일별 통계를 place_daily_stats 테이블에 삽입 또는 업데이트한다.
    public void upsertDailyStats(LocalDate targetDate,
                                 Long placeId,
                                 long starts,
                                 long arrives,
                                 long reviews,
                                 long likes,
                                 String hourlyArrivesJson,
                                 String hourlyStartsJson,
                                 Integer peakHour,
                                 OffsetDateTime aggregatedAt) {
        entityManager.createNativeQuery(UPSERT_SQL)
                .setParameter("targetDate", targetDate)
                .setParameter("placeId", placeId)
                .setParameter("starts", starts)
                .setParameter("arrives", arrives)
                .setParameter("reviews", reviews)
                .setParameter("likes", likes)
                .setParameter("hourlyArrives", hourlyArrivesJson)
                .setParameter("hourlyStarts", hourlyStartsJson)
                .setParameter("peakHour", peakHour)
                .setParameter("aggregatedAt", aggregatedAt)
                .executeUpdate();
    }
}

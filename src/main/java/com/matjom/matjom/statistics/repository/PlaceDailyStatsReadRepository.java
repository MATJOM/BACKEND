package com.matjom.matjom.statistics.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class PlaceDailyStatsReadRepository {

    @PersistenceContext
    private EntityManager entityManager;

    private static final String PATTERN_SQL = """
        SELECT date_kst, hourly_arrives, hourly_starts
        FROM place_daily_stats
        WHERE place_id = :placeId
          AND date_kst BETWEEN :startDate AND :endDate
        ORDER BY date_kst DESC
    """; // 9월 26일 최종: UC-Stat-02 패턴 조회용 쿼리

    // 지정한 기간 동안 장소의 시간대별 패턴 데이터를 불러온다.
    @SuppressWarnings("unchecked")
    public List<DailyPatternRow> fetchPatterns(Long placeId, LocalDate startDate, LocalDate endDate) {
        List<Object[]> rows = entityManager.createNativeQuery(PATTERN_SQL)
                .setParameter("placeId", placeId)
                .setParameter("startDate", startDate)
                .setParameter("endDate", endDate)
                .getResultList();

        List<DailyPatternRow> results = new ArrayList<>();
        for (Object[] row : rows) {
            LocalDate date = ((Date) row[0]).toLocalDate();
            String hourlyArrives = toJsonString(row[1]);
            String hourlyStarts = toJsonString(row[2]);
            results.add(new DailyPatternRow(date, hourlyArrives, hourlyStarts));
        }
        return results;
    }

    // DB에서 조회한 nullable JSON 값을 `{}` 형태로 정규화한다.
    private String toJsonString(Object value) {
        return value == null ? "{}" : value.toString();
    }

    public record DailyPatternRow(LocalDate date,
                                  String hourlyArrivesJson,
                                  String hourlyStartsJson) {
    }
}

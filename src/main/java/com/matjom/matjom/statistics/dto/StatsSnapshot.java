package com.matjom.matjom.statistics.dto;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 장소 통계 조회 시 DB에서 읽어온 원시 데이터를 보관하는 불변 스냅샷.
 * 사용 목적: 서비스/컨트롤러 계층에서 동일한 통계 값을 일관되게 활용하도록 전달한다.
 * 코드 의미: 누적 방문·좋아요 수와 시간대별 평균 도착 인원을 하나의 구조체로 묶는다.
 * 기대 결과: 응답 DTO로 변환할 때 기초 자료로 사용되며, 비어 있는 경우에도 안전한 기본값을 제공한다.
 */
public record StatsSnapshot(
        long totalVisitors,                  // 누적 방문자 수(ARRIVED 기록기준)
        long totalLikes,                     // 누적 좋아요 수
        Map<Integer, Long> hourlyArrivals    // 11~20시 시간대별 최근 14일 평균 도착 인원
) {
    public static StatsSnapshot empty() {
        return new StatsSnapshot(
                0L,
                0L,
                defaultHourlyMap());
    }

    private static Map<Integer, Long> defaultHourlyMap() {
        Map<Integer, Long> defaults = new LinkedHashMap<>();
        for (int hour = 11; hour <= 20; hour++) {
            defaults.put(hour, 0L);
        }
        return defaults;
    }
}

package com.matjom.matjom.statistics.dto;

public record PlaceStatsSnapshot(
        long totalVisitors,      // 9월 28일 간소화: 누적 방문자 수(ARRIVED 기록)
        long totalLikes,         // 9월 28일 간소화: 누적 좋아요 수
        long arrivals11To12,     // 9월 30일 최종: 최근 14일 평균(11~12시 도착 인원)
        long arrivals12To13      // 9월 30일 최종: 최근 14일 평균(12~13시 도착 인원)
) {
    public static PlaceStatsSnapshot empty() {
        return new PlaceStatsSnapshot(
                0L,
                0L,
                0L,
                0L);
    }
}

package com.matjom.matjom.statistics.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonDeserialize(builder = PlaceStatsResponseDTO.PlaceStatsResponseDTOBuilder.class)
public class PlaceStatsResponseDTO {

    private final String placeName;             // 9월 29일 최종: 사용자 가독성을 위한 장소 이름
    private final long totalVisitors;           // 9월 28일 간소화: 누적 방문자 수
    private final long totalLikes;              // 9월 28일 간소화: 누적 좋아요 수
    private final long arrivals11To12;          // 9월 30일 최종: 최근 14일 평균(11~12시 도착)
    private final long arrivals12To13;          // 9월 30일 최종: 최근 14일 평균(12~13시 도착)
    @JsonIgnore
    private final OffsetDateTime generatedAt;   // 9월 26일 최종: 통계 생성 시각 (내부용)
    @JsonIgnore
    private final long cacheTtlSeconds;         // 9월 26일 최종: 캐시 TTL 정보 (내부용)
    @JsonIgnore
    private final StatsDataSource dataSource;   // 9월 26일 최종: 데이터 출처 (내부용)

    public static PlaceStatsResponseDTO of(
            String placeName,
            PlaceStatsSnapshot snapshot,
            OffsetDateTime generatedAt,
            long cacheTtlSeconds,
            StatsDataSource dataSource
    ) {
        return PlaceStatsResponseDTO.builder()
                .placeName(placeName)
                .totalVisitors(snapshot.totalVisitors())
                .totalLikes(snapshot.totalLikes())
                .arrivals11To12(snapshot.arrivals11To12())
                .arrivals12To13(snapshot.arrivals12To13())
                .generatedAt(generatedAt)
                .cacheTtlSeconds(cacheTtlSeconds)
                .dataSource(dataSource)
                .build();
    }

    public PlaceStatsResponseDTO withDataSource(StatsDataSource dataSource) {
        return PlaceStatsResponseDTO.builder()
                .placeName(placeName)
                .totalVisitors(totalVisitors)
                .totalLikes(totalLikes)
                .arrivals11To12(arrivals11To12)
                .arrivals12To13(arrivals12To13)
                .generatedAt(generatedAt)
                .cacheTtlSeconds(cacheTtlSeconds)
                .dataSource(dataSource)
                .build();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class PlaceStatsResponseDTOBuilder {
        // Lombok will fill
    }
}

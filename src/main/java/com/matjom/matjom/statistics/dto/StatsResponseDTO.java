package com.matjom.matjom.statistics.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * `/stats` API 응답 페이로드를 표현하는 DTO.
 * 사용 목적: 클라이언트가 한 번의 호출로 장소 이름과 시간대별 평균을 포함한 통계를 수신하도록 한다.
 * 코드 의미: 스냅샷 데이터를 직렬화 친화적인 형태(List)로 정리하고 빌더 패턴으로 생성한다.
 * 기대 결과: 컨트롤러가 {@link StatsResponseDTO}를 반환하면 프런트는 시간대 그래프와 요약 정보를 바로 그릴 수 있다.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonDeserialize(builder = StatsResponseDTO.StatsResponseDTOBuilder.class)
public class StatsResponseDTO {

    private final String placeName;             // 9월 29일 최종: 사용자 가독성을 위한 장소 이름
    private final long totalVisitors;           // 9월 28일 간소화: 누적 방문자 수
    private final long totalLikes;              // 9월 28일 간소화: 누적 좋아요 수
    private final List<HourlyAverage> hourlyArrivals; // 9월 30일 개편: 11~20시 시간대별 평균 도착 인원

    /**
     * 스냅샷을 API 응답 형태로 변환한다.
     * 사용 목적: Domain 스냅샷을 프런트가 바로 활용할 구조로 가공한다.
     * 코드 의미: 11~20시 구간을 순회하며 누락된 시간대는 0으로 채워 일관된 리스트를 만든다.
     * 기대 결과: 항상 10개 시간대가 채워진 {@link StatsResponseDTO} 인스턴스를 생성한다.
     */
    public static StatsResponseDTO of(
            String placeName,
            StatsSnapshot snapshot
    ) {
        Map<Integer, Long> source = snapshot.hourlyArrivals();
        List<HourlyAverage> hourly = new ArrayList<>();
        for (int hour = 11; hour <= 20; hour++) {
            long count = 0L;
            if (source != null && source.containsKey(hour)) {
                count = source.getOrDefault(hour, 0L);
            }
            hourly.add(new HourlyAverage(hour, count));
        }

        return StatsResponseDTO.builder()
                .placeName(placeName)
                .totalVisitors(snapshot.totalVisitors())
                .totalLikes(snapshot.totalLikes())
                .hourlyArrivals(hourly)
                .build();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class StatsResponseDTOBuilder {
        // Lombok will fill
    }

    /**
     * 시간대별 평균 값을 표현하는 내부 DTO.
     * 사용 목적: 프런트가 시간과 평균값을 그대로 읽어 그래프나 표를 렌더링한다.
     * 코드 의미: 시간대(hour)와 평균 방문 수(averageCount)를 짝으로 보관한다.
     * 기대 결과: 리스트 요소 하나가 하나의 시간대 정보를 나타낸다.
     */
    @Getter
    @AllArgsConstructor
    public static class HourlyAverage {
        private final int hour;          // 9월 30일 개편: 시작 시각(24시간 기준)
        private final long averageCount; // 9월 30일 개편: 최근 14일 평균 도착 인원
    }
}

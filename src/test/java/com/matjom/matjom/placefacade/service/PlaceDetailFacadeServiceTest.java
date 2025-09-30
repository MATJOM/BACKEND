package com.matjom.matjom.placefacade.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.matjom.matjom.feed.dto.response.ReviewResponseDTO;
import com.matjom.matjom.feed.service.ReviewService;
import com.matjom.matjom.placefacade.dto.PlaceDetailResponseDTO;
import com.matjom.matjom.statistics.dto.PlaceStatsResponseDTO;
import com.matjom.matjom.statistics.service.PlaceStatisticsService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlaceDetailFacadeServiceTest {

    @Mock
    private PlaceStatisticsService placeStatisticsService;

    @Mock
    private ReviewService reviewService;

    private PlaceDetailFacadeService placeDetailFacadeService;

    @BeforeEach
    void setUp() {
        placeDetailFacadeService = new PlaceDetailFacadeService(placeStatisticsService, reviewService);
    }

    @Test
    // 기본 리뷰 제한값(5개)이 적용되는지와 총 리뷰 수가 유지되는지를 검증한다.
    void getPlaceDetailAppliesDefaultLimit() {
        PlaceStatsResponseDTO stats = sampleStats();
        when(placeStatisticsService.fetchPlaceStats(1L)).thenReturn(stats);

        List<ReviewResponseDTO> reviews = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            reviews.add(sampleReview(i));
        }
        when(reviewService.getPlaceReviews(1L)).thenReturn(reviews);

        PlaceDetailResponseDTO response = placeDetailFacadeService.getPlaceDetail(1L, null);

        assertThat(response.getStatistics()).isEqualTo(stats);
        assertThat(response.getTotalReviewCount()).isEqualTo(7);
        assertThat(response.getReviews()).hasSize(5);
        verify(placeStatisticsService).fetchPlaceStats(1L);
        verify(reviewService).getPlaceReviews(1L);
    }

    @Test
    // reviewLimit 파라미터로 0 이하 값을 주면 모든 리뷰를 반환하도록 동작하는지 확인한다.
    void getPlaceDetailWithoutLimitReturnsAll() {
        PlaceStatsResponseDTO stats = sampleStats();
        when(placeStatisticsService.fetchPlaceStats(anyLong())).thenReturn(stats);

        List<ReviewResponseDTO> reviews = List.of(sampleReview(1), sampleReview(2));
        when(reviewService.getPlaceReviews(anyLong())).thenReturn(reviews);

        PlaceDetailResponseDTO response = placeDetailFacadeService.getPlaceDetail(42L, 0);

        assertThat(response.getReviews()).hasSize(2);
        assertThat(response.getTotalReviewCount()).isEqualTo(2);
    }

    private PlaceStatsResponseDTO sampleStats() {
        return PlaceStatsResponseDTO.builder()
                .placeName("테스트 장소")
                .totalVisitors(120)
                .totalLikes(80)
                .hourlyArrivals(sampleHourly())
                .build();
    }

    private List<PlaceStatsResponseDTO.HourlyAverage> sampleHourly() {
        List<PlaceStatsResponseDTO.HourlyAverage> hourly = new LinkedList<>();
        for (int hour = 11; hour <= 20; hour++) {
            long count = switch (hour) {
                case 11 -> 10L;
                case 12 -> 12L;
                default -> 5L;
            };
            hourly.add(new PlaceStatsResponseDTO.HourlyAverage(hour, count));
        }
        return hourly;
    }

    private ReviewResponseDTO sampleReview(int suffix) {
        return ReviewResponseDTO.builder()
                .reviewerName("사용자" + suffix)
                .text("리뷰 내용" + suffix)
                .createdAt(OffsetDateTime.now())
                .build();
    }
}

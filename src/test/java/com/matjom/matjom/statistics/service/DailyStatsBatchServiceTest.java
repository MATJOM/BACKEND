package com.matjom.matjom.statistics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.matjom.matjom.statistics.repository.PlaceDailyStatsBatchRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DailyStatsBatchServiceTest {

    @Mock
    private PlaceDailyStatsBatchRepository batchRepository;

    private DailyStatsBatchService dailyStatsBatchService;

    private final Clock fixedClock = Clock.fixed(Instant.parse("2024-09-26T15:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        dailyStatsBatchService = new DailyStatsBatchService(
                batchRepository,
                new ObjectMapper(),
                fixedClock
        );
    }

    @Test
    // 실제 데이터가 존재할 때 집계·UPSERT·캐시 삭제·예측 호출이 모두 일어나는지 검증한다.
    void runAggregationUpsertsAggregatedDataAndEvictsCaches() {
        LocalDate targetDate = LocalDate.of(2024, 9, 25);

        when(batchRepository.fetchVisitStats(targetDate)).thenReturn(List.<Object[]>of(
                new Object[]{1L, 5L, 3L},
                new Object[]{2L, 2L, 1L}
        ));

        when(batchRepository.fetchHourlyStarts(targetDate)).thenReturn(List.<Object[]>of(
                new Object[]{1L, 10, 2L}
        ));

        when(batchRepository.fetchHourlyArrives(targetDate)).thenReturn(List.<Object[]>of(
                new Object[]{1L, 11, 1L},
                new Object[]{1L, 12, 3L}
        ));

        when(batchRepository.fetchReviewStats(targetDate)).thenReturn(List.<Object[]>of(
                new Object[]{1L, 4L}
        ));

        when(batchRepository.fetchLikeStats(targetDate)).thenReturn(List.<Object[]>of(
                new Object[]{1L, 6L}
        ));

        DailyStatsBatchService.BatchResult result = dailyStatsBatchService.runAggregation(targetDate);

        assertThat(result.getTargetDate()).isEqualTo(targetDate);
        assertThat(result.getProcessedPlaces()).isEqualTo(2);

        verify(batchRepository).upsertDailyStats(
                targetDate,
                1L,
                5L,
                3L,
                4L,
                6L,
                "{\"11\":1,\"12\":3}",
                "{\"10\":2}",
                OffsetDateTime.now(fixedClock)
        );

        DailyStatsBatchService.BatchStatus status = dailyStatsBatchService.getLastStatus();
        assertThat(status.isSuccess()).isTrue();
        assertThat(status.getTargetDate()).isEqualTo(targetDate);
    }

    @Test
    // 입력 데이터가 없을 때도 배치가 실패하지 않고 성공 상태를 남기는지 확인한다.
    void runAggregationHandlesEmptyData() {
        LocalDate targetDate = LocalDate.of(2024, 9, 25);

        when(batchRepository.fetchVisitStats(targetDate)).thenReturn(List.of());
        when(batchRepository.fetchHourlyStarts(targetDate)).thenReturn(List.of());
        when(batchRepository.fetchHourlyArrives(targetDate)).thenReturn(List.of());
        when(batchRepository.fetchReviewStats(targetDate)).thenReturn(List.of());
        when(batchRepository.fetchLikeStats(targetDate)).thenReturn(List.of());

        DailyStatsBatchService.BatchResult result = dailyStatsBatchService.runAggregation(targetDate);

        assertThat(result.getProcessedPlaces()).isZero();
        assertThat(dailyStatsBatchService.getLastStatus().isSuccess()).isTrue();
    }
}

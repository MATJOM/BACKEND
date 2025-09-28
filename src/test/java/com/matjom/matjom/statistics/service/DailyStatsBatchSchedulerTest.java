package com.matjom.matjom.statistics.service;

import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DailyStatsBatchSchedulerTest {

    @Mock
    private DailyStatsBatchService dailyStatsBatchService;

    @Test
    // 스케줄러가 KST 기준 전날 날짜를 계산해 배치 서비스를 호출하는지 확인한다.
    void runMidnightAggregationCallsServiceWithPreviousKstDate() {
        Clock clock = Clock.fixed(Instant.parse("2024-09-27T00:10:00Z"), ZoneOffset.UTC);
        DailyStatsBatchScheduler scheduler = new DailyStatsBatchScheduler(dailyStatsBatchService, clock);
        scheduler.runMidnightAggregation();
        verify(dailyStatsBatchService).runAggregation(java.time.LocalDate.of(2024, 9, 26));
    }
}

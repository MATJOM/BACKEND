package com.matjom.matjom.statistics.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailyStatsBatchScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final DailyStatsBatchService batchService;
    private final Clock clock;

    // 매일 자정(KST)에 지난 하루 통계를 집계하도록 배치 서비스 실행을 예약한다.
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void runMidnightAggregation() {
        LocalDate targetDate = LocalDate.ofInstant(clock.instant(), KST).minusDays(1);
        log.info("Running daily stats aggregation for {}", targetDate);
        batchService.runAggregation(targetDate);
    }
}

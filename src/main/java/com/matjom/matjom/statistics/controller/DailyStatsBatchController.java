package com.matjom.matjom.statistics.controller;

import com.matjom.matjom.statistics.service.DailyStatsBatchService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/batch")
public class DailyStatsBatchController {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final DailyStatsBatchService dailyStatsBatchService;
    private final Clock clock;

    // 운영자가 특정 날짜에 대해 배치를 수동으로 재실행할 때 사용한다.
    @PostMapping("/midnight-reset")
    public ResponseEntity<DailyStatsBatchService.BatchResult> runMidnightReset(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date
    ) {
        LocalDate targetDate = date != null ? date : LocalDate.ofInstant(clock.instant(), KST).minusDays(1);
        DailyStatsBatchService.BatchResult result = dailyStatsBatchService.runAggregation(targetDate);
        return ResponseEntity.ok(result);
    }

    // 최근 배치 상태를 대시보드나 헬스체크에서 조회할 수 있도록 노출한다.
    @GetMapping("/status/last")
    public ResponseEntity<DailyStatsBatchService.BatchStatus> getLastStatus() {
        return ResponseEntity.ok(dailyStatsBatchService.getLastStatus());
    }
}

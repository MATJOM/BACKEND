package com.matjom.matjom.statistics.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.matjom.matjom.statistics.service.DailyStatsBatchService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = DailyStatsBatchController.class)
@AutoConfigureMockMvc(addFilters = false)
class DailyStatsBatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DailyStatsBatchService dailyStatsBatchService;

    @MockBean
    private Clock clock;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(Instant.parse("2024-09-27T01:05:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @Test
    // 날짜 파라미터가 없을 때 컨트롤러가 기본적으로 전날 KST를 사용해 배치를 실행하는지 검증한다.
    void runMidnightResetUsesDefaultDate() throws Exception {
        LocalDate expectedDate = LocalDate.ofInstant(clock.instant(), ZoneId.of("Asia/Seoul")).minusDays(1);
        DailyStatsBatchService.BatchResult result = new DailyStatsBatchService.BatchResult(
                expectedDate,
                OffsetDateTime.parse("2024-09-26T16:05:00Z"),
                10
        );
        when(dailyStatsBatchService.runAggregation(expectedDate)).thenReturn(result);

        mockMvc.perform(post("/api/batch/midnight-reset")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.targetDate").value(expectedDate.toString()))
                .andExpect(jsonPath("$.data.processedPlaces").value(10));

        verify(dailyStatsBatchService).runAggregation(eq(expectedDate));
    }

    @Test
    // 최근 배치 상태 조회 API가 BatchStatus 정보를 그대로 반환하는지 확인한다.
    void getLastStatusReturnsStatus() throws Exception {
        DailyStatsBatchService.BatchStatus status = DailyStatsBatchService.BatchStatus.completed(
                new DailyStatsBatchService.BatchResult(
                        LocalDate.of(2024, 9, 25),
                        OffsetDateTime.parse("2024-09-26T00:10:00Z"),
                        5
                )
        );
        when(dailyStatsBatchService.getLastStatus()).thenReturn(status);

        mockMvc.perform(get("/api/batch/status/last")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.targetDate").value("2024-09-25"));
    }
}

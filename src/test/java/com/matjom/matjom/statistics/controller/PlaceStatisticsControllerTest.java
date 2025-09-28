package com.matjom.matjom.statistics.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.matjom.matjom.statistics.dto.PlaceStatsResponseDTO;
import com.matjom.matjom.statistics.dto.PlaceStatsSnapshot;
import com.matjom.matjom.statistics.dto.StatsDataSource;
import com.matjom.matjom.statistics.service.PlaceStatisticsQueryService;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = PlaceStatisticsController.class)
@AutoConfigureMockMvc(addFilters = false)
class PlaceStatisticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PlaceStatisticsQueryService statisticsQueryService;

    @Test
    // 컨트롤러가 서비스 응답을 그대로 전달하고 JSON 필드가 기대와 일치하는지 검증한다.
    void returnsPlaceStatistics() throws Exception {
        PlaceStatsResponseDTO response = PlaceStatsResponseDTO.of(
                "홍대맛집",
                new PlaceStatsSnapshot(120L, 45L, 8L, 5L),
                OffsetDateTime.parse("2024-09-26T02:30:00Z"),
                300L,
                StatsDataSource.DATABASE);
        when(statisticsQueryService.getPlaceStats(42L)).thenReturn(response);

        mockMvc.perform(get("/api/places/{placeId}/stats", 42L)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placeName").value("홍대맛집"))
                .andExpect(jsonPath("$.totalVisitors").value(120))
                .andExpect(jsonPath("$.totalLikes").value(45))
                .andExpect(jsonPath("$.arrivals11To12").value(8))
                .andExpect(jsonPath("$.arrivals12To13").value(5));

        verify(statisticsQueryService).getPlaceStats(eq(42L));
    }
}

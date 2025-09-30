package com.matjom.matjom.statistics.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.matjom.matjom.statistics.dto.PlaceStatsResponseDTO;
import com.matjom.matjom.statistics.dto.PlaceStatsSnapshot;
import java.util.LinkedHashMap;
import java.util.Map;
import com.matjom.matjom.statistics.service.PlaceStatisticsService;
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
    private PlaceStatisticsService statisticsService;

    @Test
    // 컨트롤러가 서비스 응답을 그대로 전달하고 JSON 필드가 기대와 일치하는지 검증한다.
    void returnsPlaceStatistics() throws Exception {
        PlaceStatsResponseDTO response = PlaceStatsResponseDTO.of(
                "홍대맛집",
                new PlaceStatsSnapshot(120L, 45L, buildHourly()));
        when(statisticsService.fetchPlaceStats(42L)).thenReturn(response);

        mockMvc.perform(get("/api/places/{placeId}/stats", 42L)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.placeName").value("홍대맛집"))
                .andExpect(jsonPath("$.data.totalVisitors").value(120))
                .andExpect(jsonPath("$.data.totalLikes").value(45))
                .andExpect(jsonPath("$.data.hourlyArrivals[0].hour").value(11))
                .andExpect(jsonPath("$.data.hourlyArrivals[0].averageCount").value(8))
                .andExpect(jsonPath("$.data.hourlyArrivals[1].hour").value(12))
                .andExpect(jsonPath("$.data.hourlyArrivals[1].averageCount").value(5));

        verify(statisticsService).fetchPlaceStats(eq(42L));
    }

    private Map<Integer, Long> buildHourly() {
        Map<Integer, Long> hourly = new LinkedHashMap<>();
        hourly.put(11, 8L);
        hourly.put(12, 5L);
        hourly.put(13, 3L);
        return hourly;
    }
}

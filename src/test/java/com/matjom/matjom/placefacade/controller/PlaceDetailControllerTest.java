package com.matjom.matjom.placefacade.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.matjom.matjom.feed.dto.response.ReviewResponseDTO;
import com.matjom.matjom.placefacade.dto.PlaceDetailResponseDTO;
import com.matjom.matjom.placefacade.service.PlaceDetailFacadeService;
import com.matjom.matjom.statistics.dto.PlaceStatsResponseDTO;
import com.matjom.matjom.statistics.dto.StatsDataSource;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = PlaceDetailController.class)
@AutoConfigureMockMvc(addFilters = false)
class PlaceDetailControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PlaceDetailFacadeService placeDetailFacadeService;

    @Test
    // 컨트롤러가 Facade 응답을 그대로 전달하고 reviewLimit 파라미터를 위임하는지 확인한다.
    void getPlaceDetailReturnsAggregatedResponse() throws Exception {
        PlaceStatsResponseDTO stats = PlaceStatsResponseDTO.builder()
                .placeName("맛집")
                .totalVisitors(200)
                .totalLikes(150)
                .arrivals11To12(12)
                .arrivals12To13(9)
                .generatedAt(OffsetDateTime.now())
                .cacheTtlSeconds(300)
                .dataSource(StatsDataSource.DATABASE)
                .build();

        ReviewResponseDTO review = ReviewResponseDTO.builder()
                .reviewId(UUID.randomUUID())
                .reviewerName("고객A")
                .placeName("맛집")
                .text("정말 맛있어요")
                .createdAt(OffsetDateTime.now())
                .build();

        PlaceDetailResponseDTO response = PlaceDetailResponseDTO.builder()
                .statistics(stats)
                .reviews(List.of(review))
                .totalReviewCount(1)
                .build();

        when(placeDetailFacadeService.getPlaceDetail(5L, 3)).thenReturn(response);

        mockMvc.perform(get("/api/places/{placeId}/detail", 5L)
                        .param("reviewLimit", "3")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statistics.placeName").value("맛집"))
                .andExpect(jsonPath("$.reviews[0].reviewerName").value("고객A"))
                .andExpect(jsonPath("$.totalReviewCount").value(1));

        verify(placeDetailFacadeService).getPlaceDetail(eq(5L), eq(3));
    }
}

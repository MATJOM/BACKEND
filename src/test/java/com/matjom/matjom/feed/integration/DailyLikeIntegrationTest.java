package com.matjom.matjom.feed.integration;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matjom.matjom.feed.dto.request.DailyLikeCreateRequestDTO;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class DailyLikeIntegrationTest {@Autowired
private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private final UUID userId = UUID.randomUUID();
    private static final ZoneId KST = ZoneId.of("Asia/Seoul"); // 수정제안 2024-09-24: 테스트에서도 KST 기준 사용.

    @Test
    @DisplayName("좋아요 생성→조회→취소 플로우가 ApiResponse 규약으로 정상 동작한다")
    void likeFlow() throws Exception {
        DailyLikeCreateRequestDTO request = new DailyLikeCreateRequestDTO(1L, 1000L);
        LocalDate todayKst = LocalDate.now(KST); // 수정제안 2024-09-24: 고정 Clock 대신 실제 KST 날짜 사용.

        String responseBody = mockMvc.perform(post("/api/v1/likes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .requestAttr("userId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.visitId", is(1000)))
                .andExpect(jsonPath("$.data.dateKst", is(todayKst.toString()))) // 수정제안 2024-09-24
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(responseBody);
        UUID likeId = UUID.fromString(root.path("data").path("id").asText());

        mockMvc.perform(get("/api/v1/likes/my/place/{placeId}", 1L)
                        .requestAttr("userId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data[0].id", is(likeId.toString())))
                .andExpect(jsonPath("$.data[0].status", is("ACTIVE")))
                .andExpect(jsonPath("$.data[0].dateKst", is(todayKst.toString())));
        // 수정제안 2024-09-24

        mockMvc.perform(delete("/api/v1/likes/{likeId}", likeId)
                        .requestAttr("userId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        mockMvc.perform(get("/api/v1/likes/my/place/{placeId}", 1L)
                        .requestAttr("userId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("동일 방문에 두 번 좋아요 생성 시 409와 LIKE_ALREADY_EXISTS 코드가 반환된다")
    void duplicateLikeReturnsConflict() throws Exception {
        DailyLikeCreateRequestDTO request = new DailyLikeCreateRequestDTO(2L, 2000L);

        mockMvc.perform(post("/api/v1/likes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .requestAttr("userId", userId))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/likes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .requestAttr("userId", userId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("LIKE_ALREADY_EXISTS")));
    }
}
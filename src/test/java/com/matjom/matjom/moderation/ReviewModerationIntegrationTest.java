package com.matjom.matjom.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matjom.matjom.feed.entity.review.Review;
import com.matjom.matjom.feed.entity.review.ReviewStatus;
import com.matjom.matjom.feed.repository.ReviewRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "moderation.review.hide-threshold=2",
        "moderation.review.delete-threshold=3"
})
@Transactional
public class ReviewModerationIntegrationTest {
    private static final UUID WRITER_ID = UUID.randomUUID();
    private static final UUID REPORTER_BASE = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ReviewModerationService reviewModerationService;

    @Test
    @DisplayName("금칙어 포함 리뷰는 400(REVIEW_BAD_LANGUAGE) 반환")
    void createReviewFailsWhenProfanityDetected() throws Exception {
        String body = """
                {
                  "placeId": 1,
                  "visitId": 10,
                  "text": "욕설1 들어간 리뷰입니다"
                }
                """;

        mockMvc.perform(post("/api/v1/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .requestAttr("userId", WRITER_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("REVIEW_BAD_LANGUAGE")));
    }

    @Test
    @DisplayName("신고 API는 신고 이력만 남기고 리뷰 상태는 유지된다")
    void reportReviewKeepsStatusActive() throws Exception {
        UUID reviewId = createReview(); // 9월26일 수정제안: 리뷰 작성 헬퍼 활용

        String reportBody = """
                {
                  "reason": "SPAM",
                  "description": "광고입니다"
                }
                """;
        UUID reporterId = UUID.nameUUIDFromBytes((REPORTER_BASE.toString() + "1").getBytes());

        mockMvc.perform(post("/api/v1/reviews/{reviewId}/reports", reviewId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportBody)
                        .requestAttr("userId", reporterId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                // 9월26일 수정제안: 신고 후에도 상태/경고 수는 그대로
                .andExpect(jsonPath("$.data.reviewStatus", is("ACTIVE")))
                .andExpect(jsonPath("$.data.warningCount", is(0)));

        Review review = reviewRepository.findById(reviewId).orElseThrow();
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.ACTIVE);
        assertThat(review.getWarningCount()).isZero();
    }

    @Test
    @DisplayName("관리자 경고 누적 시 숨김과 삭제 임계치가 적용된다")
    void manualWarningThresholdsChangeStatus() throws Exception {
        UUID reviewId = createReview();

        // 9월26일 수정제안: 경고 2회 → 숨김
        reviewModerationService.issueManualWarning(reviewId, "첫 번째 경고");
        reviewModerationService.issueManualWarning(reviewId, "두 번째 경고");

        Review hidden = reviewRepository.findById(reviewId).orElseThrow();
        assertThat(hidden.getWarningCount()).isEqualTo(2);
        assertThat(hidden.getStatus()).isEqualTo(ReviewStatus.HIDDEN);
        assertThat(hidden.getFlagged()).isTrue();

        // 9월26일 수정제안: 경고 3회 → 삭제
        reviewModerationService.issueManualWarning(reviewId, "세 번째 경고");

        Review deleted = reviewRepository.findById(reviewId).orElseThrow();
        assertThat(deleted.getWarningCount()).isEqualTo(3);
        assertThat(deleted.getStatus()).isEqualTo(ReviewStatus.DELETED);
        assertThat(deleted.isDeleted()).isTrue();
    }

    private UUID createReview() throws Exception {
        String createBody = """
                {
                  "placeId": 2,
                  "visitId": 20,
                  "text": "맛있게 잘 먹었습니다"
                }
                """;

        String response = mockMvc.perform(post("/api/v1/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody)
                        .requestAttr("userId", WRITER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        return UUID.fromString(root.path("data").path("id").asText());
    }
}

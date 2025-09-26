package com.matjom.matjom.feed.dto.response;

import com.matjom.matjom.feed.entity.review.Review;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewResponseDTO {
    private UUID reviewId;      // 9월 26일 최종: ID 유지
    private String reviewerName; // 9월 26일 최종: 사용자 이름 제공
    private String placeName;    // 9월 26일 최종: 장소 이름 제공
    private String text;
    private OffsetDateTime createdAt;

    public static ReviewResponseDTO of(Review review, String reviewerName, String placeName) {
        return ReviewResponseDTO.builder()
                .reviewId(review.getId())
                .reviewerName(reviewerName)
                .placeName(placeName)
                .text(review.getText())
                .createdAt(review.getCreatedAt())
                .build();
    }
}

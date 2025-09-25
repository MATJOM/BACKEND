package com.matjom.matjom.feed.dto.response;

import com.matjom.matjom.feed.entity.review.Review;
import com.matjom.matjom.feed.entity.review.ReviewStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewResponseDTO {
    private UUID id;
    private UUID userId;
    private Long placeId;
    private Long visitId;
    private String text;
    private ReviewStatus status;
    private Boolean flagged;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static ReviewResponseDTO from(Review review) {
        return ReviewResponseDTO.builder()
                .id(review.getId())
                .userId(review.getUserId())
                .placeId(review.getPlaceId())
                .visitId(review.getVisitId())
                .text(review.getText())
                .status(review.getStatus())
                .flagged(review.getFlagged())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }
}

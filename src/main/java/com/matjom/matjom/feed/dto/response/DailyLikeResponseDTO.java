package com.matjom.matjom.feed.dto.response;

import com.matjom.matjom.feed.entity.likes.DailyLike;
import com.matjom.matjom.feed.entity.likes.LikeStatus;
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
public class DailyLikeResponseDTO {
    private UUID likeId; // 9월 26일 최종: 핵심 식별자만 제공
    private Long placeId;
    private Long visitId;
    private LikeStatus status;
    private OffsetDateTime createdAt;

    public static DailyLikeResponseDTO from(DailyLike like) {
        return DailyLikeResponseDTO.builder()
                .likeId(like.getId())
                .placeId(like.getPlaceId())
                .visitId(like.getVisitId())
                .status(like.getStatus())
                .createdAt(like.getCreatedAt())
                .build();
    }
}

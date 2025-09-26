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
    private UUID likeId;
    private String userName;
    private String placeName;
    private Long visitId;
    private LikeStatus status;
    private OffsetDateTime createdAt;

    public static DailyLikeResponseDTO of(DailyLike like, String userName, String placeName) {
        return DailyLikeResponseDTO.builder()
                .likeId(like.getId())
                .userName(userName)
                .placeName(placeName)
                .visitId(like.getVisitId())
                .status(like.getStatus())
                .createdAt(like.getCreatedAt())
                .build();
    }
}

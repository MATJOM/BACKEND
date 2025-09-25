package com.matjom.matjom.feed.dto.response;

import com.matjom.matjom.feed.entity.likes.DailyLike;
import com.matjom.matjom.feed.entity.likes.LikeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyLikeResponseDTO {
    private UUID id;
    private UUID userId;
    private Long placeId;
    private Long visitId;
    private LocalDate dateKst;
    private LikeStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime cancelledAt;

    public static DailyLikeResponseDTO from(DailyLike dailyLike) {
        return DailyLikeResponseDTO.builder()
                .id(dailyLike.getId())
                .userId(dailyLike.getUserId())
                .placeId(dailyLike.getPlaceId())
                .visitId(dailyLike.getVisitId())
                .dateKst(dailyLike.getDateKst())
                .status(dailyLike.getStatus())
                .createdAt(dailyLike.getCreatedAt())
                .cancelledAt(dailyLike.getCancelledAt())
                .build();
    }
}

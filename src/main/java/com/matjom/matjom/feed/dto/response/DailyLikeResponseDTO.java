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

    // 목적: 좋아요 엔티티를 응답 DTO로 변환한다
    // 필요 이유: 서비스에서 매번 builder를 작성하는 반복을 줄이고 일관된 필드 구성을 보장한다
    // 로직: 엔티티 필드와 조회한 이름 값을 빌더에 채워 DTO를 생성한다
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

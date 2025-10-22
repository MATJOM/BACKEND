package com.matjom.matjom.feed.dto.response;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 좋아요 상태 조회 응답 DTO.
 * 사용 목적: 특정 사용자·장소 조합에 대해 좋아요 여부와 식별자를 전달한다.
 * 코드 의미: 불린 플래그와 좋아요 UUID를 묶어 직렬화 시 필요한 데이터를 제공한다.
 * 기대 결과: 프런트가 좋아요 버튼 UI 상태와 식별자를 일관되게 유지한다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LikeStatusResponseDTO {
    private boolean liked;
    private UUID likeId;
}

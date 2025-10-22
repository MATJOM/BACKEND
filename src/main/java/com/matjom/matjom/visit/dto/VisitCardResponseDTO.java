package com.matjom.matjom.visit.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 방문 기록 카드(UI 타일)에 필요한 정보를 모은 DTO.
 * 사용 목적: 리스트/상세에서 리뷰·좋아요 상태와 권한을 한 번에 표시한다.
 * 코드 의미: 방문 ID, 장소명, ARRIVED 시각뿐 아니라 리뷰/좋아요 가능 여부를 플래그로 담아 프런트 조건문을 단순화한다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VisitCardResponseDTO {
    private Long visitId;
    private Long placeId;
    private String placeName;
    private OffsetDateTime arrivedAt;
    private boolean reviewed;
    private UUID reviewId;
    private UUID likeId;
    private boolean liked;
    private boolean reviewAllowed;
    private boolean likeAllowed;
    private boolean reviewEditable;
    private boolean reviewDeletable;
}

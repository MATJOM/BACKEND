package com.matjom.matjom.feed.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 좋아요 생성 요청 본문을 표현하는 DTO.
 * 사용 목적: 특정 장소(및 방문 기록) 좋아요 요청을 수신해 서비스 계층으로 전달한다.
 * 코드 의미: 필수 필드인 장소 ID를 검증하고 선택 필드 visitId를 보관한다.
 * 기대 결과: 검증된 데이터만 좋아요 생성 로직으로 전달돼 중복 오류를 줄인다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LikeCreateRequestDTO {

    @NotNull(message = "장소 ID는 필수입니다")
    private Long placeId;

    private Long visitId;
}

package com.matjom.matjom.visit.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 방문 카드 목록을 감싸는 응답 DTO.
 * 사용 목적: API 응답 스펙을 명확히 하고, 향후 메타데이터를 확장할 수 있도록 래퍼 구조를 제공한다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VisitListResponseDTO {
    private List<VisitCardResponseDTO> visits;
}

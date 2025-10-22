package com.matjom.matjom.visit.dto;

import com.matjom.matjom.visit.entity.VisitState;
import java.time.OffsetDateTime;

/**
 * 수동 도착 확정 결과를 전달하는 응답 DTO.
 * 사용 목적: 세션 상태가 어떻게 변했는지, 언제 도착으로 기록됐는지, 멱등 재생 여부를 알린다.
 * 코드 의미: `replayed` 플래그로 중복 요청이었는지 분기할 수 있다.
 */
public record VisitManualArrivalResponse(Long sessionId,
                                          VisitState state,
                                          OffsetDateTime arrivedAt,
                                          String requestedBy,
                                          boolean replayed) {
}

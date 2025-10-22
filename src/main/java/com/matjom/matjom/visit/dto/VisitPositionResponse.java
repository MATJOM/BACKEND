package com.matjom.matjom.visit.dto;

import com.matjom.matjom.visit.entity.VisitState;
import java.time.OffsetDateTime;

/**
 * 위치 샘플 기록 결과를 반환하는 DTO.
 * 사용 목적: 세션 상태, 누적 체류 시간(dwell), 정확도 일시정지 여부를 프런트에 알려 즉각 UI 반영을 가능하게 한다.
 */
public record VisitPositionResponse(Long positionId,
                                    Long sessionId,
                                    VisitState state,
                                    OffsetDateTime recordedAt,
                                    long dwellSeconds,
                                    boolean accuracyPaused) {
}

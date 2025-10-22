package com.matjom.matjom.visit.dto;

import com.matjom.matjom.visit.entity.VisitState;
import java.time.OffsetDateTime;

/**
 * 방문 세션 생성 결과를 전달하는 응답 DTO.
 * 사용 목적: 세션 ID와 만료 시각, 멱등 재생 여부를 프런트가 바로 확인할 수 있게 한다.
 * 코드 의미: 멱등 재생일 경우 `replayed=true`로 표기해 추가 안내나 토스트를 제어할 수 있다.
 */
public record VisitSessionStartResponse(Long sessionId,
                                        VisitState state,
                                        OffsetDateTime startedAt,
                                        OffsetDateTime expiresAt,
                                        boolean replayed) {
}

package com.matjom.matjom.visit.api;

import com.matjom.matjom.common.exception.base.SessionException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.common.response.ApiResponse;
import com.matjom.matjom.visit.dto.VisitManualArrivalRequest;
import com.matjom.matjom.visit.dto.VisitManualArrivalResponse;
import com.matjom.matjom.visit.dto.VisitPositionRequest;
import com.matjom.matjom.visit.dto.VisitPositionResponse;
import com.matjom.matjom.visit.dto.VisitSessionStartRequest;
import com.matjom.matjom.visit.dto.VisitSessionStartResponse;
import com.matjom.matjom.visit.service.VisitSessionService;
import com.matjom.matjom.visit.service.VisitPositionService;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/sessions")
@Validated
@RequiredArgsConstructor
public class VisitSessionController {

    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 200; // 멱등 키 최대 길이

    private final VisitSessionService visitSessionService;
    private final VisitPositionService visitPositionService;

    @PostMapping
    public ApiResponse<VisitSessionStartResponse> startSession(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody VisitSessionStartRequest request) {
        String sanitizedKey = validateIdempotencyKey(idempotencyKey); // 공백/길이 검증
        VisitSessionStartResponse response = visitSessionService.startSession(request, sanitizedKey); // 멱등 처리 포함 서비스 호출
        return ApiResponse.ok(response); // 공통 응답 포맷
    }

    @PostMapping("/{sessionId}/positions")
    public ApiResponse<VisitPositionResponse> recordPosition(
            @PathVariable("sessionId") Long sessionId,
            @Valid @RequestBody VisitPositionRequest request) {
        VisitPositionResponse response = visitPositionService.recordPosition(sessionId, request); //위치 이벤트 저장 + 자동 도착 판정
        return ApiResponse.ok(response);
    }

    @PostMapping("/{sessionId}/arrivals")
    public ApiResponse<VisitManualArrivalResponse> confirmArrival(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable("sessionId") Long sessionId,
            @Valid @RequestBody VisitManualArrivalRequest request) {
        String sanitizedKey = validateIdempotencyKey(idempotencyKey);
        VisitManualArrivalResponse response = visitSessionService.confirmManualArrival(sessionId, request, sanitizedKey); // 수동 도착도 멱등 키 필수
        return ApiResponse.ok(response);
    }

    private String validateIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) { // 헤더 누락 or 공백만이면 예외
            throw new SessionException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }
        String trimmed = idempotencyKey.trim();
        if (trimmed.length() > IDEMPOTENCY_KEY_MAX_LENGTH) { // 200자 초과 방지
            throw new SessionException(ErrorCode.INVALID_REQUEST_PARAM, "Idempotency-Key 길이는 200자를 초과할 수 없습니다.");
        }
        return trimmed;
    }
}

package com.matjom.matjom.visit.api;

import com.matjom.matjom.common.exception.base.SessionException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.common.response.ApiResponse;
import com.matjom.matjom.visit.dto.VisitSessionStartRequest;
import com.matjom.matjom.visit.dto.VisitSessionStartResponse;
import com.matjom.matjom.visit.service.VisitSessionService;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sessions")
@Validated
public class VisitSessionController {

    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 200;

    private final VisitSessionService visitSessionService;

    public VisitSessionController(VisitSessionService visitSessionService) {
        this.visitSessionService = visitSessionService;
    }

    @PostMapping
    public ApiResponse<VisitSessionStartResponse> startSession(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody VisitSessionStartRequest request) {
        String sanitizedKey = validateIdempotencyKey(idempotencyKey);
        VisitSessionStartResponse response = visitSessionService.startSession(request, sanitizedKey);
        return ApiResponse.ok(response);
    }

    private String validateIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new SessionException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }
        String trimmed = idempotencyKey.trim();
        if (trimmed.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new SessionException(ErrorCode.INVALID_REQUEST_PARAM, "Idempotency-Key 길이는 200자를 초과할 수 없습니다.");
        }
        return trimmed;
    }
}

package com.matjom.matjom.recommendation.api;

import com.matjom.matjom.common.exception.base.RecommendationException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.common.response.ApiResponse;
import com.matjom.matjom.recommendation.dto.RouletteRequest;
import com.matjom.matjom.recommendation.dto.RouletteResponse;
import com.matjom.matjom.recommendation.service.RouletteService;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/recommendations")
@Validated
@RequiredArgsConstructor
public class RouletteController {

    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 200;

    private final RouletteService rouletteService;

    @PostMapping("/roulette")
    public ApiResponse<RouletteResponse> postRoulette(@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
                                                      @Valid @RequestBody RouletteRequest request) {
        String sanitizedKey = validateIdempotencyKey(idempotencyKey); // 빈 헤더/200자 초과 방지
        RouletteResponse response = rouletteService.recommend(request, sanitizedKey);// 멱등 키와 함께 서비스 호출
        return ApiResponse.ok(response); // 공통 응답 규약 적용
    }

    private String validateIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) { // 헤더 누락 → IDEMPOTENCY_KEY_REQUIRED
            throw new RecommendationException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }
        String trimmed = idempotencyKey.trim();
        if (trimmed.length() > IDEMPOTENCY_KEY_MAX_LENGTH) { // 200자 초과 방지
            throw new RecommendationException(ErrorCode.INVALID_REQUEST_PARAM, "Idempotency-Key 길이는 200자를 초과할 수 없습니다.");
        }
        return trimmed;
    }
}

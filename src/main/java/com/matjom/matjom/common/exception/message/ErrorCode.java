package com.matjom.matjom.common.exception.message;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // ====== 공통 ======
    INVALID_REQUEST_PARAM(HttpStatus.BAD_REQUEST, "잘못된 요청 파라미터입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 부족합니다."),

    // ====== Auth / User ======
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 존재하는 이메일입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    USER_INACTIVE(HttpStatus.BAD_REQUEST, "비활성화된 계정입니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "리프레시 토큰이 만료되었습니다."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "리프레시 토큰이 존재하지 않습니다."),
    PASSWORD_MISMATCH(HttpStatus.UNAUTHORIZED, "기존 비밀번호가 일치하지 않습니다."),
    WITHDRAW_ALREADY_INACTIVE(HttpStatus.BAD_REQUEST, "이미 탈퇴 처리된 계정입니다."),

    // ====== Session / Geo ======
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "세션을 찾을 수 없습니다."),
    SESSION_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 진행 중인 세션이 있습니다."),
    SESSION_EXPIRED(HttpStatus.BAD_REQUEST, "세션이 만료되었습니다."),
    SESSION_ALREADY_INACTIVE(HttpStatus.CONFLICT, "이미 종료된 세션입니다."),
    GEO_PERMISSION_DENIED(HttpStatus.FORBIDDEN, "위치 권한이 허용되지 않았습니다."),
    GPS_SIGNAL_LOST(HttpStatus.BAD_REQUEST, "GPS 신호를 잃었습니다."),
    INVALID_GEOFENCE(HttpStatus.BAD_REQUEST, "잘못된 지오펜스 요청입니다."),
    ARRIVAL_NOT_CONFIRMED(HttpStatus.BAD_REQUEST, "도착이 확정되지 않았습니다."),
    ARRIVAL_MISDETECTED(HttpStatus.CONFLICT, "잘못된 도착 감지가 발생했습니다."),
    ARRIVAL_CANCELLED(HttpStatus.OK, "세션이 취소되었습니다."),
    ARRIVAL_TIME_INVALID(HttpStatus.BAD_REQUEST, "수동 도착은 시작 10~60분 사이에만 가능합니다."),
    ARRIVAL_DISTANCE_EXCEEDED(HttpStatus.BAD_REQUEST, "수동 도착 가능 반경(30m)을 초과했습니다."),
    NETWORK_UNSTABLE(HttpStatus.BAD_GATEWAY, "네트워크가 불안정합니다."),
    SESSION_RECOVERY_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "세션 복구에 실패했습니다."),

    // ====== Place / Search ======
    PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "가게 정보를 찾을 수 없습니다."),
    INVALID_CATEGORY(HttpStatus.BAD_REQUEST, "잘못된 카테고리 요청입니다."),
    SEARCH_EMPTY_RESULT(HttpStatus.OK, "검색 결과가 없습니다."),
    SEARCH_RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "요청 제한을 초과했습니다."),
    SEARCH_RELAX_NOT_CONSENTED(HttpStatus.BAD_REQUEST, "빈 결과 완화 동의가 필요합니다."),
    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "Idempotency-Key 헤더가 필요합니다."),
    IDEMPOTENCY_KEY_CONFLICT(HttpStatus.CONFLICT, "동일한 Idempotency-Key로 다른 요청이 전달되었습니다."),
    ROULETTE_NO_CANDIDATE(HttpStatus.NO_CONTENT, "룰렛 후보가 없습니다."),

    // ====== Feed ======
    REVIEW_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "리뷰 작성 권한이 없습니다."),
    REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT, "리뷰가 이미 작성되었습니다."),
    REVIEW_BAD_LANGUAGE(HttpStatus.BAD_REQUEST, "부적절한 표현이 감지되었습니다."),
    LIKE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "좋아요 권한이 없습니다."),
    LIKE_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 좋아요를 누른 상태입니다."),
    OPPORTUNITY_EXHAUSTED(HttpStatus.BAD_REQUEST, "오늘의 기회를 모두 사용했습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}

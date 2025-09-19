package com.matjom.matjom.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.matjom.matjom.common.exception.message.ErrorCode;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        ErrorPayload error,
        Instant timestamp
) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, Instant.now());
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null, Instant.now());
    }

    public static ApiResponse<Void> error(ErrorCode code) {
        return error(code, code.getDefaultMessage());
    }

    public static ApiResponse<Void> error(ErrorCode code, String message) {
        return error(code, message, null);
    }

    public static ApiResponse<Void> error(ErrorCode code, String message, String detail) {
        return new ApiResponse<>(false, null, new ErrorPayload(code.name(), message, detail), Instant.now());
    }

    public static <T> ApiResponse<T> error(ErrorCode code, String message, T data) {
        return new ApiResponse<>(false, data, new ErrorPayload(code.name(), message, null), Instant.now());
    }
}

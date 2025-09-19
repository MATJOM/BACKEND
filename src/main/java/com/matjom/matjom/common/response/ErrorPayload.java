package com.matjom.matjom.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorPayload(String code, String message, String detail) {

    public ErrorPayload(String code, String message) {
        this(code, message, null);
    }
}

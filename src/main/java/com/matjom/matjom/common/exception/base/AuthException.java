package com.matjom.matjom.common.exception.base;

import com.matjom.matjom.common.exception.message.ErrorCode;

public class AuthException extends DomainException {
    public AuthException(ErrorCode errorCode) {
        super(errorCode);
    }

    public AuthException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}

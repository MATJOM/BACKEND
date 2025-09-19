package com.matjom.matjom.common.exception.base;

import com.matjom.matjom.common.exception.message.ErrorCode;

public class SessionException extends DomainException {
    public SessionException(ErrorCode errorCode) {
        super(errorCode);
    }

    public SessionException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}

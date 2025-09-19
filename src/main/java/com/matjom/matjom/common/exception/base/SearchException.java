package com.matjom.matjom.common.exception.base;

import com.matjom.matjom.common.exception.message.ErrorCode;

public class SearchException extends DomainException {
    public SearchException(ErrorCode errorCode) {
        super(errorCode);
    }

    public SearchException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}

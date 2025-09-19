package com.matjom.matjom.common.exception.base;

import com.matjom.matjom.common.exception.message.ErrorCode;

public class PlaceException extends DomainException {
    public PlaceException(ErrorCode errorCode) {
        super(errorCode);
    }

    public PlaceException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}

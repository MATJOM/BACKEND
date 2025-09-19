package com.matjom.matjom.common.exception.base;

import com.matjom.matjom.common.exception.message.ErrorCode;

public class GeoException extends DomainException {
    public GeoException(ErrorCode errorCode) {
        super(errorCode);
    }

    public GeoException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}

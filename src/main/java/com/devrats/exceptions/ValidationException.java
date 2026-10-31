package com.devrats.exceptions;

public class ValidationException extends DomainException {

    public ValidationException(String message) {
        super(ErrorCode.VALIDATION_ERROR, message);
    }
}

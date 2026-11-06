package com.devrats.exceptions;

public class EmailDeliveryException extends RuntimeException {

    private final String code;
    private final Integer status;
    private final boolean retryable;

    public EmailDeliveryException(String code, String message, Integer status, boolean retryable) {
        super(message);
        this.code = code;
        this.status = status;
        this.retryable = retryable;
    }

    public EmailDeliveryException(String code, String message, Integer status, boolean retryable, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.status = status;
        this.retryable = retryable;
    }

    public String getCode() {
        return code;
    }

    public Integer getStatus() {
        return status;
    }

    public boolean isRetryable() {
        return retryable;
    }
}

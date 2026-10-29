package com.devstreak.exceptions;

import java.time.Duration;

public class RateLimitException extends DomainException {

    private final Duration retryAfter;

    public RateLimitException(Duration retryAfter) {
        super(ErrorCode.RATE_LIMITED);
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}

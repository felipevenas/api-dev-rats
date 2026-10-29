package com.devstreak.exceptions;

public class GitHubApiException extends DomainException {

    private final int statusCode;

    public GitHubApiException(int statusCode, String message) {
        super(ErrorCode.GITHUB_UNAVAILABLE, message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}

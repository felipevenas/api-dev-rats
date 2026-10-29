package com.devstreak.utils;

import org.springframework.http.ResponseCookie;

import java.time.Duration;

public final class RefreshCookieHelper {

    public static final String COOKIE_NAME = "ds_refresh";
    public static final String COOKIE_PATH = "/api/v1/auth";

    private RefreshCookieHelper() {
    }

    public static ResponseCookie create(String token, Duration ttl, boolean secure) {
        return ResponseCookie.from(COOKIE_NAME, token)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(ttl)
                .build();
    }

    public static ResponseCookie clear(boolean secure) {
        return ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .build();
    }
}

package com.devrats.utils;

import java.security.SecureRandom;
import java.util.Base64;

public final class TokenHelper {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private TokenHelper() {
    }

    public static String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hashToken(String token) {
        return HashingHelper.sha256Hex(token);
    }
}

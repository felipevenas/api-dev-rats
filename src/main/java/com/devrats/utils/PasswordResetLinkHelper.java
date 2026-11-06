package com.devrats.utils;

public final class PasswordResetLinkHelper {

    private PasswordResetLinkHelper() {
    }

    /**
     * O token vai no fragmento para não ser enviado ao servidor, não aparecer
     * em logs de proxy/CDN e não vazar pelo header {@code Referer}.
     */
    public static String build(String frontendUrl, String rawToken) {
        String base = frontendUrl == null ? "" : frontendUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/reset-password#token=" + rawToken;
    }
}

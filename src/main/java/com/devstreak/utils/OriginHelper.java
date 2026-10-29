package com.devstreak.utils;

import java.util.List;

public final class OriginHelper {

    private OriginHelper() {
    }

    //---- Requests without Origin (curl, server-to-server) carry no browser cookie risk.
    public static boolean isAllowed(String origin, List<String> allowedOrigins) {
        if (origin == null || origin.isBlank()) {
            return true;
        }
        return allowedOrigins.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .anyMatch(value -> value.equalsIgnoreCase(origin));
    }
}

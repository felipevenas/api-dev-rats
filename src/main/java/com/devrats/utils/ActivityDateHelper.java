package com.devrats.utils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public final class ActivityDateHelper {

    //---- Default zone for new records: the product operates on Brasília time.
    public static final String DEFAULT_TIMEZONE = "America/Sao_Paulo";

    private ActivityDateHelper() {
    }

    //---- A day belongs to the challenge timezone, never to the server timezone.
    public static LocalDate toActivityDate(Instant instant, ZoneId challengeZone) {
        return instant.atZone(challengeZone).toLocalDate();
    }

    public static ZoneId parseZone(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (Exception e) {
            throw new IllegalArgumentException("Fuso horário inválido: " + timezone, e);
        }
    }

    /**
     * Resolves the timezone to persist on new records.
     * Missing or blank values fall back to {@link #DEFAULT_TIMEZONE} so clients that omit the field
     * keep working; informed values are validated and normalized. Historical zones stay allowed.
     */
    public static String resolveTimezone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return DEFAULT_TIMEZONE;
        }
        return parseZone(timezone.trim()).getId();
    }
}

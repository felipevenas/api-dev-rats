package com.devstreak.utils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public final class ActivityDateHelper {

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
}

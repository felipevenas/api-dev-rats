package com.devstreak.utils;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityDateHelperTest {

    @Test
    void shouldUseChallengeTimezone_whenInstantCrossesUtcDayBoundary() {
        Instant instant = Instant.parse("2026-01-01T02:30:00Z");

        assertThat(ActivityDateHelper.toActivityDate(instant, ZoneId.of("America/Sao_Paulo")))
                .isEqualTo(LocalDate.of(2025, 12, 31));
        assertThat(ActivityDateHelper.toActivityDate(instant, ZoneId.of("Europe/Lisbon")))
                .isEqualTo(LocalDate.of(2026, 1, 1));
    }
}

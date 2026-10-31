package com.devrats.utils;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActivityDateHelperTest {

    @Test
    void shouldUseChallengeTimezone_whenInstantCrossesUtcDayBoundary() {
        Instant instant = Instant.parse("2026-01-01T02:30:00Z");

        assertThat(ActivityDateHelper.toActivityDate(instant, ZoneId.of("America/Sao_Paulo")))
                .isEqualTo(LocalDate.of(2025, 12, 31));
        assertThat(ActivityDateHelper.toActivityDate(instant, ZoneId.of("Europe/Lisbon")))
                .isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    void shouldFallbackToBrasilia_whenTimezoneIsMissingOrBlank() {
        assertThat(ActivityDateHelper.resolveTimezone(null)).isEqualTo("America/Sao_Paulo");
        assertThat(ActivityDateHelper.resolveTimezone("")).isEqualTo("America/Sao_Paulo");
        assertThat(ActivityDateHelper.resolveTimezone("   ")).isEqualTo("America/Sao_Paulo");
    }

    @Test
    void shouldKeepInformedTimezone_whenValid() {
        assertThat(ActivityDateHelper.resolveTimezone("America/Sao_Paulo")).isEqualTo("America/Sao_Paulo");
        assertThat(ActivityDateHelper.resolveTimezone("Europe/Lisbon")).isEqualTo("Europe/Lisbon");
        assertThat(ActivityDateHelper.resolveTimezone("UTC")).isEqualTo("UTC");
    }

    @Test
    void shouldRejectInvalidTimezone() {
        assertThatThrownBy(() -> ActivityDateHelper.resolveTimezone("Mars/Olympus"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Fuso horário inválido");
    }
}

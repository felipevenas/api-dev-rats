package com.devstreak.utils;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StreakHelperTest {

    @Test
    void shouldCalculateCurrentAndBestStreak_whenDatesAreConsecutive() {
        List<LocalDate> dates = List.of(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 3));

        StreakHelper.StreakResult result = StreakHelper.calculate(dates, LocalDate.of(2026, 1, 3));

        assertThat(result.current()).isEqualTo(3);
        assertThat(result.best()).isEqualTo(3);
    }

    @Test
    void shouldBreakCurrentStreak_whenLastActivityIsOlderThanYesterday() {
        List<LocalDate> dates = List.of(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2));

        StreakHelper.StreakResult result = StreakHelper.calculate(dates, LocalDate.of(2026, 1, 5));

        assertThat(result.current()).isZero();
        assertThat(result.best()).isEqualTo(2);
    }

    @Test
    void shouldKeepBestStreak_whenCurrentStreakIsShorter() {
        List<LocalDate> dates = List.of(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 4));

        StreakHelper.StreakResult result = StreakHelper.calculate(dates, LocalDate.of(2026, 1, 4));

        assertThat(result.current()).isEqualTo(1);
        assertThat(result.best()).isEqualTo(2);
    }

    @Test
    void shouldReturnZero_whenThereIsNoActivity() {
        StreakHelper.StreakResult result = StreakHelper.calculate(List.of(), LocalDate.of(2026, 1, 1));

        assertThat(result.current()).isZero();
        assertThat(result.best()).isZero();
    }
}

package com.devstreak.utils;

import java.time.LocalDate;
import java.util.List;
import java.util.TreeSet;

public final class StreakHelper {

    private StreakHelper() {
    }

    public record StreakResult(int current, int best) {
    }

    //---- Missing a day breaks the current streak but never erases accumulated valid days.
    public static StreakResult calculate(List<LocalDate> validDates, LocalDate referenceDate) {
        if (validDates == null || validDates.isEmpty()) {
            return new StreakResult(0, 0);
        }
        TreeSet<LocalDate> dates = new TreeSet<>(validDates);
        int best = 1;
        int run = 1;
        LocalDate previous = null;
        for (LocalDate date : dates) {
            if (previous != null && previous.plusDays(1).equals(date)) {
                run++;
            } else {
                run = 1;
            }
            best = Math.max(best, run);
            previous = date;
        }

        LocalDate last = dates.last();
        int current = 0;
        if (!last.isBefore(referenceDate.minusDays(1))) {
            current = 1;
            LocalDate cursor = last;
            while (dates.contains(cursor.minusDays(1))) {
                current++;
                cursor = cursor.minusDays(1);
            }
        }
        return new StreakResult(current, best);
    }
}

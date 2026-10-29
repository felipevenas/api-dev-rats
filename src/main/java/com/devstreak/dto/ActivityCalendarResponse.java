package com.devstreak.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ActivityCalendarResponse(
        UUID challengeId,
        UUID userId,
        String timezone,
        LocalDate from,
        LocalDate to,
        List<ActivityDayResponse> days
) {
}

package com.devrats.dto;

import java.time.LocalDate;

public record ActivityDayResponse(LocalDate date, String status) {
}

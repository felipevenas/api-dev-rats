package com.devrats.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirm(
        @NotBlank String token,
        @NotBlank @Size(min = 10, max = 128) String newPassword
) {
}

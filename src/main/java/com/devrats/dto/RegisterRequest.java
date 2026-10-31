package com.devrats.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(min = 10, max = 128) String password,
        @NotBlank @Size(min = 2, max = 120) String displayName,
        @NotBlank @Pattern(regexp = "^[a-z0-9_]{3,30}$", message = "use 3-30 caracteres: letras minúsculas, números ou _") String username,
        @Size(max = 64) String timezone,
        @NotBlank @Size(max = 20) String acceptedTermsVersion
) {
}

package com.devrats.dto;

import com.devrats.enums.ExperienceLevel;
import com.devrats.enums.TechStack;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(min = 10, max = 128) String password,
        @NotBlank @Size(min = 2, max = 120) String displayName,
        @NotBlank @Pattern(regexp = "^[a-z0-9_]{3,30}$", message = "use 3-30 caracteres: letras minúsculas, números ou _") String username,
        @Size(max = 64) String timezone,
        @NotBlank @Size(max = 20) String acceptedTermsVersion,
        @Size(max = 280) String bio,
        @Size(max = 120) String location,
        @Size(max = 300) @Pattern(regexp = "^$|^https?://\\S+$", message = "deve ser uma URL http ou https") String websiteUrl,
        ExperienceLevel experienceLevel,
        @Size(max = 5, message = "informe no máximo 5 tecnologias") List<@NotNull TechStack> primaryStack
) {
}

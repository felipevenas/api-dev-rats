package com.devrats.dto;

import com.devrats.enums.ExperienceLevel;
import com.devrats.enums.TechStack;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Atualização parcial do perfil: campos nulos são ignorados.
 *
 * <p>Regras de limpeza: {@code bio}/{@code location}/{@code websiteUrl} com string vazia
 * viram {@code null}; {@code primaryStack} com lista vazia limpa a stack. Para limpar o
 * {@code experienceLevel}, envie {@code clearExperienceLevel: true}.</p>
 */
public record UpdateUserRequest(
        @Size(min = 2, max = 120) String displayName,
        @Size(max = 64) String timezone,
        Boolean notifyDailyGoal,
        Boolean notifyMilestones,
        Boolean notifyChallengeUpdates,
        Boolean notifyIntegration,
        @Size(max = 280) String bio,
        @Size(max = 120) String location,
        @Size(max = 300) @Pattern(regexp = "^$|^https?://\\S+$", message = "deve ser uma URL http ou https") String websiteUrl,
        ExperienceLevel experienceLevel,
        @Size(max = 5, message = "informe no máximo 5 tecnologias") List<@NotNull TechStack> primaryStack,
        Boolean clearExperienceLevel
) {
}

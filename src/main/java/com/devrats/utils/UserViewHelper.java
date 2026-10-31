package com.devrats.utils;

import com.devrats.dto.UserResponse;
import com.devrats.entities.GitHubAccountEntity;
import com.devrats.entities.UserEntity;
import com.devrats.enums.GitHubConnectionStatus;
import com.devrats.enums.TechStack;

public final class UserViewHelper {

    private UserViewHelper() {
    }

    public static UserResponse toResponse(UserEntity user, GitHubAccountEntity githubAccount) {
        boolean connected = githubAccount != null
                && githubAccount.getConnectionStatus() == GitHubConnectionStatus.CONNECTED;
        return new UserResponse(
                user.getId(),
                user.getDisplayName(),
                user.getUsername(),
                user.getEmail(),
                user.getAvatarUrl(),
                user.getAvatarSource() == null ? "NONE" : user.getAvatarSource().name(),
                user.getBio(),
                user.getLocation(),
                user.getWebsiteUrl(),
                user.getExperienceLevel(),
                TechStack.fromCsv(user.getPrimaryStack()),
                user.getTimezone(),
                user.isNotifyDailyGoal(),
                user.isNotifyMilestones(),
                user.isNotifyChallengeUpdates(),
                user.isNotifyIntegration(),
                connected,
                connected ? githubAccount.getGithubUsername() : null,
                user.getCreatedAt());
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    /**
     * Normaliza campos opcionais de texto: string em branco vira {@code null},
     * o restante é armazenado sem espaços nas pontas.
     */
    public static String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

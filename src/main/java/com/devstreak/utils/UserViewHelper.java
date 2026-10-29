package com.devstreak.utils;

import com.devstreak.dto.UserResponse;
import com.devstreak.entities.GitHubAccountEntity;
import com.devstreak.entities.UserEntity;
import com.devstreak.enums.GitHubConnectionStatus;

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
}

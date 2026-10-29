package com.devstreak.utils;

import com.devstreak.dto.ChallengeProgressResponse;
import com.devstreak.dto.ChallengeRepositoryResponse;
import com.devstreak.dto.ChallengeResponse;
import com.devstreak.entities.ChallengeEntity;
import com.devstreak.entities.ChallengeParticipantEntity;
import com.devstreak.entities.ChallengeRepositoryEntity;

import java.util.List;

public final class ChallengeViewHelper {

    private ChallengeViewHelper() {
    }

    public static ChallengeResponse toResponse(ChallengeEntity challenge,
                                               List<ChallengeRepositoryEntity> repositories,
                                               int participantCount) {
        List<ChallengeRepositoryResponse> repositoryResponses = repositories.stream()
                .map(ChallengeViewHelper::toRepositoryResponse)
                .toList();
        return new ChallengeResponse(challenge.getId(), challenge.getGroup().getId(), challenge.getSequenceNo(),
                challenge.getVerificationMode(), challenge.getTargetValidDays(), challenge.getTimezone(),
                challenge.getStatus(), challenge.getStartsAt(), challenge.getEndsAt(), challenge.getAdmissionEndsAt(),
                challenge.getStartedAt(), challenge.getCompletedAt(), challenge.getCancelledAt(), challenge.isAllowForks(),
                repositoryResponses, participantCount);
    }

    public static ChallengeRepositoryResponse toRepositoryResponse(ChallengeRepositoryEntity repository) {
        return new ChallengeRepositoryResponse(repository.getId(), repository.getGithubRepository().getId(),
                repository.getGithubRepository().getGithubRepositoryId(),
                repository.getGithubRepository().getFullName(), repository.getGithubRepository().getOwnerLogin(),
                repository.getSlot());
    }

    public static ChallengeProgressResponse toProgress(ChallengeParticipantEntity participant, int target, int rank) {
        int percent = target <= 0 ? 0 : Math.min(100, (participant.getValidDays() * 100) / target);
        return new ChallengeProgressResponse(participant.getId(), participant.getUser().getId(),
                participant.getUser().getDisplayName(), participant.getUser().getUsername(),
                participant.getUser().getAvatarUrl(), participant.getValidDays(), target, percent,
                participant.getCurrentStreak(), participant.getBestStreak(), participant.getLastValidDate(),
                participant.getCompletedAt(), rank);
    }
}

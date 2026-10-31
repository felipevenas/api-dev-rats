package com.devrats.services.activity;

import com.devrats.config.AppProperties;
import com.devrats.entities.ChallengeEntity;
import com.devrats.entities.ChallengeParticipantEntity;
import com.devrats.entities.GitHubRepositoryEntity;
import com.devrats.enums.ChallengeStatus;
import com.devrats.enums.GitHubOwnerType;
import com.devrats.enums.ParticipantStatus;
import com.devrats.enums.RepositoryAccessStatus;
import com.devrats.enums.VerificationMode;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class CommitEligibilityPolicy {

    private final AppProperties properties;

    public CommitEligibilityPolicy(AppProperties properties) {
        this.properties = properties;
    }

    public EligibilityDecision evaluate(ChallengeEntity challenge,
                                        ChallengeParticipantEntity participant,
                                        GitHubRepositoryEntity repository,
                                        Long participantGithubId,
                                        boolean repositoryLinked,
                                        CommitCandidate candidate,
                                        Instant now) {
        if (challenge.getStatus() != ChallengeStatus.ACTIVE) {
            return EligibilityDecision.deny("CHALLENGE_NOT_ACTIVE");
        }
        if (participant.getStatus() != ParticipantStatus.ACTIVE) {
            return EligibilityDecision.deny("PARTICIPANT_NOT_ACTIVE");
        }
        if (repository.getAccessStatus() != RepositoryAccessStatus.ACCESSIBLE) {
            return EligibilityDecision.deny("REPOSITORY_NOT_ACCESSIBLE");
        }
        if (candidate.githubAuthorId() == null || !candidate.githubAuthorId().equals(participantGithubId)) {
            return EligibilityDecision.deny("AUTHOR_MISMATCH");
        }
        if (candidate.automated()) {
            return EligibilityDecision.deny("AUTOMATED_COMMIT");
        }
        if (candidate.empty()) {
            return EligibilityDecision.deny("EMPTY_COMMIT");
        }
        if (candidate.merge()) {
            return EligibilityDecision.deny("MERGE_COMMIT");
        }
        if (candidate.committedAt().isBefore(challenge.getStartsAt())) {
            return EligibilityDecision.deny("BEFORE_START");
        }
        if (candidate.committedAt().isBefore(now.minus(properties.getChallenges().getMaxCommitAge()))) {
            return EligibilityDecision.deny("COMMIT_TOO_OLD");
        }
        if (participant.getJoinedAt() != null && candidate.committedAt().isBefore(participant.getJoinedAt())) {
            return EligibilityDecision.deny("BEFORE_PARTICIPANT_JOIN");
        }
        if (candidate.committedAt().isAfter(now.plus(properties.getChallenges().getCommitClockSkew()))) {
            return EligibilityDecision.deny("FUTURE_COMMIT");
        }
        if (challenge.getEndsAt() != null && candidate.committedAt().isAfter(challenge.getEndsAt())) {
            return EligibilityDecision.deny("AFTER_END");
        }
        if (challenge.getVerificationMode() == VerificationMode.SELECTED_REPOSITORIES) {
            return repositoryLinked ? EligibilityDecision.allow() : EligibilityDecision.deny("REPOSITORY_NOT_SELECTED");
        }
        if (repository.getOwnerType() != GitHubOwnerType.USER
                || repository.getOwnerGithubUserId() == null
                || !repository.getOwnerGithubUserId().equals(participantGithubId)) {
            return EligibilityDecision.deny("REPOSITORY_NOT_OWNED_BY_PARTICIPANT");
        }
        if (repository.isArchived()) {
            return EligibilityDecision.deny("REPOSITORY_ARCHIVED");
        }
        if (repository.isFork() && !challenge.isAllowForks()) {
            return EligibilityDecision.deny("FORK_NOT_ALLOWED");
        }
        return EligibilityDecision.allow();
    }
}

package com.devstreak.services.activity;

import com.devstreak.config.AppProperties;
import com.devstreak.entities.ChallengeEntity;
import com.devstreak.entities.ChallengeParticipantEntity;
import com.devstreak.entities.GitHubRepositoryEntity;
import com.devstreak.enums.ChallengeStatus;
import com.devstreak.enums.CommitObservationSource;
import com.devstreak.enums.GitHubOwnerType;
import com.devstreak.enums.ParticipantStatus;
import com.devstreak.enums.RepositoryAccessStatus;
import com.devstreak.enums.VerificationMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CommitEligibilityPolicyTest {

    private static final Long GITHUB_USER_ID = 1001L;
    private static final Instant NOW = Instant.parse("2026-10-28T12:00:00Z");

    private CommitEligibilityPolicy policy;
    private ChallengeEntity challenge;
    private ChallengeParticipantEntity participant;
    private GitHubRepositoryEntity repository;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.getChallenges().setCommitClockSkew(Duration.ofMinutes(5));
        policy = new CommitEligibilityPolicy(properties);

        challenge = new ChallengeEntity();
        challenge.setStatus(ChallengeStatus.ACTIVE);
        challenge.setVerificationMode(VerificationMode.OWN_REPOSITORIES);
        challenge.setStartsAt(NOW.minus(Duration.ofDays(1)));
        challenge.setTimezone("America/Sao_Paulo");

        participant = new ChallengeParticipantEntity();
        participant.setStatus(ParticipantStatus.ACTIVE);

        repository = new GitHubRepositoryEntity();
        repository.setOwnerType(GitHubOwnerType.USER);
        repository.setOwnerGithubUserId(GITHUB_USER_ID);
        repository.setAccessStatus(RepositoryAccessStatus.ACCESSIBLE);
    }

    @Test
    void shouldAcceptOwnRepositoryCommit_whenAuthorMatchesOwner() {
        EligibilityDecision decision = policy.evaluate(challenge, participant, repository, GITHUB_USER_ID,
                false, candidate(GITHUB_USER_ID, false, false, false), NOW);

        assertThat(decision.eligible()).isTrue();
    }

    @Test
    void shouldRejectCommit_whenAuthorDoesNotMatchParticipant() {
        EligibilityDecision decision = policy.evaluate(challenge, participant, repository, GITHUB_USER_ID,
                false, candidate(2002L, false, false, false), NOW);

        assertThat(decision.reason()).isEqualTo("AUTHOR_MISMATCH");
    }

    @Test
    void shouldRejectCommit_whenRepositoryBelongsToOrganization() {
        repository.setOwnerType(GitHubOwnerType.ORGANIZATION);
        repository.setOwnerGithubUserId(9000L);

        EligibilityDecision decision = policy.evaluate(challenge, participant, repository, GITHUB_USER_ID,
                false, candidate(GITHUB_USER_ID, false, false, false), NOW);

        assertThat(decision.reason()).isEqualTo("REPOSITORY_NOT_OWNED_BY_PARTICIPANT");
    }

    @Test
    void shouldRejectFork_whenChallengeDoesNotAllowForks() {
        repository.setFork(true);

        EligibilityDecision decision = policy.evaluate(challenge, participant, repository, GITHUB_USER_ID,
                false, candidate(GITHUB_USER_ID, false, false, false), NOW);

        assertThat(decision.reason()).isEqualTo("FORK_NOT_ALLOWED");
    }

    @Test
    void shouldRejectAutomatedEmptyAndMergeCommits() {
        assertThat(policy.evaluate(challenge, participant, repository, GITHUB_USER_ID, false,
                candidate(GITHUB_USER_ID, true, false, false), NOW).reason()).isEqualTo("AUTOMATED_COMMIT");
        assertThat(policy.evaluate(challenge, participant, repository, GITHUB_USER_ID, false,
                candidate(GITHUB_USER_ID, false, true, false), NOW).reason()).isEqualTo("EMPTY_COMMIT");
        assertThat(policy.evaluate(challenge, participant, repository, GITHUB_USER_ID, false,
                candidate(GITHUB_USER_ID, false, false, true), NOW).reason()).isEqualTo("MERGE_COMMIT");
    }

    @Test
    void shouldRejectCommit_whenItIsOlderThanTheAllowedObservationWindow() {
        challenge.setStartsAt(NOW.minus(Duration.ofDays(10)));
        CommitCandidate candidate = new CommitCandidate("sha", GITHUB_USER_ID, null, "tester",
                NOW.minus(Duration.ofDays(5)), NOW, false, false, false, CommitObservationSource.WEBHOOK);

        assertThat(policy.evaluate(challenge, participant, repository, GITHUB_USER_ID, false, candidate, NOW).reason())
                .isEqualTo("COMMIT_TOO_OLD");
    }

    @Test
    void shouldRejectCommit_whenItHappenedBeforeParticipantJoined() {
        participant.setJoinedAt(NOW.minus(Duration.ofHours(1)));
        CommitCandidate candidate = new CommitCandidate("sha", GITHUB_USER_ID, null, "tester",
                NOW.minus(Duration.ofHours(2)), NOW, false, false, false, CommitObservationSource.WEBHOOK);

        assertThat(policy.evaluate(challenge, participant, repository, GITHUB_USER_ID, false, candidate, NOW).reason())
                .isEqualTo("BEFORE_PARTICIPANT_JOIN");
    }

    @Test
    void shouldRejectCommit_beforeChallengeStart() {
        CommitCandidate candidate = new CommitCandidate("sha", GITHUB_USER_ID, null, "tester",
                NOW.minus(Duration.ofDays(2)), NOW, false, false, false, CommitObservationSource.WEBHOOK);

        assertThat(policy.evaluate(challenge, participant, repository, GITHUB_USER_ID, false, candidate, NOW).reason())
                .isEqualTo("BEFORE_START");
    }

    @Test
    void shouldRequireLinkedRepository_inSelectedMode() {
        challenge.setVerificationMode(VerificationMode.SELECTED_REPOSITORIES);

        assertThat(policy.evaluate(challenge, participant, repository, GITHUB_USER_ID, false,
                candidate(GITHUB_USER_ID, false, false, false), NOW).reason()).isEqualTo("REPOSITORY_NOT_SELECTED");
        assertThat(policy.evaluate(challenge, participant, repository, GITHUB_USER_ID, true,
                candidate(GITHUB_USER_ID, false, false, false), NOW).eligible()).isTrue();
    }

    private CommitCandidate candidate(Long authorId, boolean automated, boolean empty, boolean merge) {
        return new CommitCandidate("abc123", authorId, null, "tester", NOW.minus(Duration.ofMinutes(10)),
                NOW, automated, empty, merge, CommitObservationSource.WEBHOOK);
    }
}

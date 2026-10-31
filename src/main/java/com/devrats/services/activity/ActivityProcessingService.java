package com.devrats.services.activity;

import com.devrats.dto.github.GitHubCommit;
import com.devrats.dto.github.StoredPushPayload;
import com.devrats.entities.ChallengeEntity;
import com.devrats.entities.ChallengeParticipantEntity;
import com.devrats.entities.GitHubAccountEntity;
import com.devrats.entities.GitHubRepositoryEntity;
import com.devrats.entities.UserEntity;
import com.devrats.enums.AuditAction;
import com.devrats.enums.ChallengeStatus;
import com.devrats.enums.CommitObservationSource;
import com.devrats.enums.EligibilityStatus;
import com.devrats.enums.NotificationType;
import com.devrats.enums.ParticipantStatus;
import com.devrats.enums.VerificationMode;
import com.devrats.repositories.ChallengeParticipantRepository;
import com.devrats.repositories.ChallengeRepositoryRepository;
import com.devrats.repositories.EligibleCommitRepository;
import com.devrats.repositories.GitHubAccountRepository;
import com.devrats.repositories.GitHubRepositoryRepository;
import com.devrats.repositories.MemberDailyActivityRepository;
import com.devrats.services.AuditService;
import com.devrats.services.ChallengeCompletionService;
import com.devrats.services.NotificationService;
import com.devrats.services.ProgressService;
import com.devrats.services.github.GitHubApiClient;
import com.devrats.services.github.GitHubAppTokenProvider;
import com.devrats.services.github.GitHubPayloadParser;
import com.devrats.utils.ActivityDateHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ActivityProcessingService {

    private static final Logger log = LoggerFactory.getLogger(ActivityProcessingService.class);
    private static final Pattern BOT_LOGIN = Pattern.compile("(?i)(\\[bot\\]|dependabot|renovate|github-actions|web-flow)");
    private static final Set<Integer> MILESTONES = Set.of(7, 14, 30, 60, 100, 365);

    private final GitHubPayloadParser payloadParser;
    private final GitHubApiClient apiClient;
    private final GitHubAppTokenProvider tokenProvider;
    private final GitHubRepositoryRepository repositoryRepository;
    private final GitHubAccountRepository accountRepository;
    private final ChallengeRepositoryRepository challengeRepositoryRepository;
    private final ChallengeParticipantRepository participantRepository;
    private final EligibleCommitRepository eligibleCommitRepository;
    private final MemberDailyActivityRepository activityRepository;
    private final CommitEligibilityPolicy eligibilityPolicy;
    private final ProgressService progressService;
    private final ChallengeCompletionService completionService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final Clock clock;

    public ActivityProcessingService(GitHubPayloadParser payloadParser,
                                     GitHubApiClient apiClient,
                                     GitHubAppTokenProvider tokenProvider,
                                     GitHubRepositoryRepository repositoryRepository,
                                     GitHubAccountRepository accountRepository,
                                     ChallengeRepositoryRepository challengeRepositoryRepository,
                                     ChallengeParticipantRepository participantRepository,
                                     EligibleCommitRepository eligibleCommitRepository,
                                     MemberDailyActivityRepository activityRepository,
                                     CommitEligibilityPolicy eligibilityPolicy,
                                     ProgressService progressService,
                                     ChallengeCompletionService completionService,
                                     NotificationService notificationService,
                                     AuditService auditService,
                                     Clock clock) {
        this.payloadParser = payloadParser;
        this.apiClient = apiClient;
        this.tokenProvider = tokenProvider;
        this.repositoryRepository = repositoryRepository;
        this.accountRepository = accountRepository;
        this.challengeRepositoryRepository = challengeRepositoryRepository;
        this.participantRepository = participantRepository;
        this.eligibleCommitRepository = eligibleCommitRepository;
        this.activityRepository = activityRepository;
        this.eligibilityPolicy = eligibilityPolicy;
        this.progressService = progressService;
        this.completionService = completionService;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public void processPush(String storedPayload) {
        StoredPushPayload payload = payloadParser.parseStored(storedPayload);
        if (payload.repository() == null || payload.repository().id() == null || payload.commits() == null) {
            return;
        }
        GitHubRepositoryEntity repository = repositoryRepository
                .findByGithubRepositoryId(payload.repository().id())
                .orElse(null);
        if (repository == null) {
            log.warn("Push event ignored: repository {} is not known", payload.repository().id());
            return;
        }
        for (StoredPushPayload.StoredCommit storedCommit : payload.commits()) {
            processStoredCommit(repository, storedCommit);
        }
    }

    @Transactional
    public void processReconciledCommit(ChallengeEntity challenge, GitHubRepositoryEntity repository, GitHubCommit commit) {
        if (commit.sha() == null || commit.commit() == null) {
            return;
        }
        Instant committedAt = commit.commit().committer() != null && commit.commit().committer().date() != null
                ? commit.commit().committer().date()
                : commit.commit().author() == null ? null : commit.commit().author().date();
        if (committedAt == null) {
            return;
        }
        String login = commit.author() == null ? null : commit.author().login();
        GitHubCommit detailed = commit;
        //---- The commit list endpoint omits stats; fetch the detail to detect empty commits.
        if (commit.stats() == null && repository.getInstallationId() != null) {
            String token = tokenProvider.getInstallationToken(repository.getInstallationId());
            detailed = apiClient.getCommit(token, repository.getFullName(), commit.sha());
        }
        boolean empty = detailed.stats() != null && detailed.stats().total() != null && detailed.stats().total() == 0;
        CommitCandidate candidate = new CommitCandidate(commit.sha(),
                commit.author() == null ? null : commit.author().id(),
                commit.committer() == null ? null : commit.committer().id(),
                login, committedAt, clock.instant(), isAutomated(login), empty,
                commit.parents() != null && commit.parents().size() > 1, CommitObservationSource.RECONCILIATION);
        if (candidate.githubAuthorId() == null) {
            return;
        }
        GitHubAccountEntity account = accountRepository.findByGithubUserId(candidate.githubAuthorId()).orElse(null);
        if (account == null) {
            return;
        }
        ChallengeParticipantEntity participant = participantRepository
                .findByChallengeIdAndUserId(challenge.getId(), account.getUser().getId())
                .filter(item -> item.getStatus() == ParticipantStatus.ACTIVE)
                .orElse(null);
        if (participant == null) {
            return;
        }
        boolean linked = challengeRepositoryRepository.existsByChallengeIdAndGithubRepositoryId(challenge.getId(),
                repository.getId());
        persistVerification(repository, candidate, commit.commit().message());
        processForChallenge(challenge, participant, repository, candidate, linked);
    }

    private void processStoredCommit(GitHubRepositoryEntity repository, StoredPushPayload.StoredCommit storedCommit) {
        Instant committedAt = parseInstant(storedCommit.timestamp());
        if (storedCommit.id() == null || committedAt == null) {
            return;
        }
        int added = storedCommit.added() == null ? 0 : storedCommit.added();
        int removed = storedCommit.removed() == null ? 0 : storedCommit.removed();
        int modified = storedCommit.modified() == null ? 0 : storedCommit.modified();
        String login = storedCommit.author() == null ? null : storedCommit.author().username();
        String committerLogin = storedCommit.committer() == null ? null : storedCommit.committer().username();
        boolean automated = isAutomated(login) || isAutomated(committerLogin);
        boolean merge = storedCommit.parentCount() != null && storedCommit.parentCount() > 1;
        boolean empty = added + removed + modified == 0;
        Long authorId = resolveAuthorId(repository, storedCommit.id());
        CommitCandidate candidate = new CommitCandidate(storedCommit.id(), authorId, null, login, committedAt,
                clock.instant(), automated, empty, merge, CommitObservationSource.WEBHOOK);
        if (authorId == null) {
            log.debug("Commit {} has no verified GitHub author; skipping", storedCommit.id());
            return;
        }
        persistVerification(repository, candidate, null);
        GitHubAccountEntity account = accountRepository.findByGithubUserId(authorId).orElse(null);
        if (account == null) {
            return;
        }
        Map<UUID, ChallengeEntity> challenges = candidateChallenges(repository, account.getUser());
        for (ChallengeEntity challenge : challenges.values()) {
            ChallengeParticipantEntity participant = participantRepository
                    .findByChallengeIdAndUserId(challenge.getId(), account.getUser().getId())
                    .filter(item -> item.getStatus() == ParticipantStatus.ACTIVE)
                    .orElse(null);
            if (participant == null) {
                continue;
            }
            boolean linked = challengeRepositoryRepository.existsByChallengeIdAndGithubRepositoryId(challenge.getId(),
                    repository.getId());
            processForChallenge(challenge, participant, repository, candidate, linked);
        }
    }

    private void processForChallenge(ChallengeEntity challenge, ChallengeParticipantEntity participant,
                                     GitHubRepositoryEntity repository, CommitCandidate candidate, boolean linked) {
        EligibilityDecision decision = eligibilityPolicy.evaluate(challenge, participant, repository,
                resolveParticipantGithubId(participant), linked, candidate, clock.instant());
        if (!decision.eligible()) {
            log.debug("Commit {} rejected for challenge {}: {}", candidate.sha(), challenge.getId(), decision.reason());
            return;
        }
        ZoneId zone = ActivityDateHelper.parseZone(challenge.getTimezone());
        LocalDate activityDate = ActivityDateHelper.toActivityDate(candidate.committedAt(), zone);
        int inserted = activityRepository.insertIfAbsent(challenge.getId(), participant.getId(),
                participant.getUser().getId(), activityDate, candidate.committedAt());
        if (inserted == 0) {
            activityRepository.increment(challenge.getId(), participant.getId(), activityDate, candidate.committedAt());
        }
        progressService.recalculateParticipant(participant.getId());
        notificationService.notify(participant.getUser().getId(), NotificationType.DAILY_GOAL, challenge.getId(),
                "daily-goal:" + challenge.getId() + ":" + participant.getId() + ":" + activityDate,
                Map.of("activityDate", activityDate.toString()));
        auditService.record(participant.getUser().getId(), "CHALLENGE", challenge.getId(), AuditAction.DAILY_GOAL,
                Map.of("activityDate", activityDate.toString()));
        int validDays = participantRepository.findById(participant.getId())
                .map(ChallengeParticipantEntity::getValidDays)
                .orElse(0);
        if (MILESTONES.contains(validDays)) {
            notificationService.notify(participant.getUser().getId(), NotificationType.MILESTONE, challenge.getId(),
                    "milestone:" + challenge.getId() + ":" + participant.getId() + ":" + validDays,
                    Map.of("validDays", validDays));
        }
        completionService.checkAndComplete(challenge.getId());
    }

    private Map<UUID, ChallengeEntity> candidateChallenges(GitHubRepositoryEntity repository, UserEntity user) {
        Map<UUID, ChallengeEntity> challenges = new LinkedHashMap<>();
        challengeRepositoryRepository.findAllByGithubRepositoryId(repository.getId()).stream()
                .map(link -> link.getChallenge())
                .filter(challenge -> challenge.getStatus() == ChallengeStatus.ACTIVE)
                .forEach(challenge -> challenges.put(challenge.getId(), challenge));
        participantRepository.findAllByUserIdAndStatus(user.getId(), ParticipantStatus.ACTIVE).stream()
                .map(ChallengeParticipantEntity::getChallenge)
                .filter(challenge -> challenge.getStatus() == ChallengeStatus.ACTIVE)
                .filter(challenge -> challenge.getVerificationMode() == VerificationMode.OWN_REPOSITORIES)
                .forEach(challenge -> challenges.put(challenge.getId(), challenge));
        return challenges;
    }

    private Long resolveParticipantGithubId(ChallengeParticipantEntity participant) {
        return accountRepository.findByUserId(participant.getUser().getId())
                .map(GitHubAccountEntity::getGithubUserId)
                .orElse(null);
    }

    private Long resolveAuthorId(GitHubRepositoryEntity repository, String sha) {
        if (repository.getInstallationId() == null) {
            return null;
        }
        String token = tokenProvider.getInstallationToken(repository.getInstallationId());
        GitHubCommit commit = apiClient.getCommit(token, repository.getFullName(), sha);
        return commit.author() == null ? null : commit.author().id();
    }

    private void persistVerification(GitHubRepositoryEntity repository, CommitCandidate candidate, String message) {
        String status = candidate.automated() || candidate.empty() || candidate.merge()
                ? EligibilityStatus.INELIGIBLE.name() : EligibilityStatus.ELIGIBLE.name();
        String reason = candidate.automated() ? "AUTOMATED_COMMIT"
                : candidate.empty() ? "EMPTY_COMMIT"
                : candidate.merge() ? "MERGE_COMMIT" : null;
        eligibleCommitRepository.insertIfAbsent(repository.getId(), candidate.sha(), candidate.githubAuthorId(),
                candidate.githubCommitterId(), candidate.authorLogin(), candidate.committedAt(),
                candidate.firstObservedAt(), candidate.automated(), status, reason, candidate.source().name());
    }

    private boolean isAutomated(String login) {
        return login != null && BOT_LOGIN.matcher(login).find();
    }

    private Instant parseInstant(String value) {
        try {
            return value == null ? null : Instant.parse(value);
        } catch (Exception e) {
            return null;
        }
    }
}

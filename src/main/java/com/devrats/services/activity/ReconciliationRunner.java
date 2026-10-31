package com.devrats.services.activity;

import com.devrats.config.AppProperties;
import com.devrats.dto.github.GitHubCommit;
import com.devrats.entities.ChallengeEntity;
import com.devrats.entities.ChallengeParticipantEntity;
import com.devrats.entities.ChallengeRepositoryEntity;
import com.devrats.entities.GitHubAccountEntity;
import com.devrats.entities.GitHubRepositoryEntity;
import com.devrats.entities.ReconciliationRunEntity;
import com.devrats.enums.ChallengeStatus;
import com.devrats.enums.ParticipantStatus;
import com.devrats.enums.ReconciliationStatus;
import com.devrats.repositories.ChallengeParticipantRepository;
import com.devrats.repositories.ChallengeRepository;
import com.devrats.repositories.ChallengeRepositoryRepository;
import com.devrats.repositories.GitHubAccountRepository;
import com.devrats.repositories.GitHubRepositoryRepository;
import com.devrats.repositories.ReconciliationRunRepository;
import com.devrats.services.github.GitHubApiClient;
import com.devrats.services.github.GitHubAppTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReconciliationRunner {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationRunner.class);

    private final ChallengeRepository challengeRepository;
    private final ChallengeRepositoryRepository challengeRepositoryRepository;
    private final ChallengeParticipantRepository participantRepository;
    private final GitHubRepositoryRepository repositoryRepository;
    private final GitHubAccountRepository accountRepository;
    private final ReconciliationRunRepository runRepository;
    private final GitHubApiClient apiClient;
    private final GitHubAppTokenProvider tokenProvider;
    private final ActivityProcessingService activityProcessingService;
    private final AppProperties properties;
    private final Clock clock;

    public ReconciliationRunner(ChallengeRepository challengeRepository,
                                ChallengeRepositoryRepository challengeRepositoryRepository,
                                ChallengeParticipantRepository participantRepository,
                                GitHubRepositoryRepository repositoryRepository,
                                GitHubAccountRepository accountRepository,
                                ReconciliationRunRepository runRepository,
                                GitHubApiClient apiClient,
                                GitHubAppTokenProvider tokenProvider,
                                ActivityProcessingService activityProcessingService,
                                AppProperties properties,
                                Clock clock) {
        this.challengeRepository = challengeRepository;
        this.challengeRepositoryRepository = challengeRepositoryRepository;
        this.participantRepository = participantRepository;
        this.repositoryRepository = repositoryRepository;
        this.accountRepository = accountRepository;
        this.runRepository = runRepository;
        this.apiClient = apiClient;
        this.tokenProvider = tokenProvider;
        this.activityProcessingService = activityProcessingService;
        this.properties = properties;
        this.clock = clock;
    }

    public void reconcile(UUID challengeId) {
        ChallengeEntity challenge = challengeRepository.findById(challengeId).orElse(null);
        if (challenge == null || challenge.getStatus() != ChallengeStatus.ACTIVE) {
            return;
        }
        Instant since = runRepository.findTopByChallengeIdOrderByStartedAtDesc(challengeId)
                .map(previous -> previous.getStartedAt().minusSeconds(300))
                .orElseGet(() -> challenge.getStartedAt() == null
                        ? clock.instant().minus(properties.getReconciliation().getInitialLookback())
                        : challenge.getStartedAt());
        ReconciliationRunEntity run = new ReconciliationRunEntity();
        run.setChallenge(challenge);
        run.setStatus(ReconciliationStatus.RUNNING);
        runRepository.save(run);

        try {
            Map<UUID, GitHubRepositoryEntity> repositories = repositoriesFor(challenge);
            int found = 0;
            for (GitHubRepositoryEntity repository : repositories.values()) {
                found += reconcileRepository(challenge, repository, since);
            }
            run.setCommitsFound(found);
            run.setStatus(ReconciliationStatus.SUCCEEDED);
            run.setFinishedAt(clock.instant());
            runRepository.save(run);
        } catch (RuntimeException exception) {
            log.warn("Reconciliation failed for challenge {}: {}", challengeId, exception.getMessage());
            run.setStatus(ReconciliationStatus.FAILED);
            run.setFinishedAt(clock.instant());
            run.setErrorCode(exception.getClass().getSimpleName());
            runRepository.save(run);
        }
    }

    private Map<UUID, GitHubRepositoryEntity> repositoriesFor(ChallengeEntity challenge) {
        Map<UUID, GitHubRepositoryEntity> repositories = new LinkedHashMap<>();
        if (challenge.getVerificationMode() == com.devrats.enums.VerificationMode.SELECTED_REPOSITORIES) {
            challengeRepositoryRepository.findAllByChallengeIdOrderBySlotAsc(challenge.getId())
                    .stream()
                    .map(ChallengeRepositoryEntity::getGithubRepository)
                    .forEach(repository -> repositories.put(repository.getId(), repository));
            return repositories;
        }
        List<ChallengeParticipantEntity> participants = participantRepository
                .findAllByChallengeIdAndStatus(challenge.getId(), ParticipantStatus.ACTIVE);
        for (ChallengeParticipantEntity participant : participants) {
            accountRepository.findByUserId(participant.getUser().getId())
                    .map(GitHubAccountEntity::getGithubUserId)
                    .ifPresent(githubUserId -> repositoryRepository.findAllByOwnerGithubUserId(githubUserId)
                            .forEach(repository -> repositories.put(repository.getId(), repository)));
        }
        return repositories;
    }

    private int reconcileRepository(ChallengeEntity challenge, GitHubRepositoryEntity repository, Instant since) {
        if (repository.getInstallationId() == null) {
            return 0;
        }
        String token = tokenProvider.getInstallationToken(repository.getInstallationId());
        int found = 0;
        for (int page = 1; page <= 3; page++) {
            List<GitHubCommit> commits = apiClient.listCommits(token, repository.getFullName(), since, page,
                    properties.getReconciliation().getPageSize());
            for (GitHubCommit commit : commits) {
                activityProcessingService.processReconciledCommit(challenge, repository, commit);
                found++;
            }
            if (commits.size() < properties.getReconciliation().getPageSize()) {
                break;
            }
        }
        return found;
    }
}

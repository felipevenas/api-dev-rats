package com.devrats.services.activity;

import com.devrats.IntegrationTestBase;
import com.devrats.dto.CreateChallengeRequest;
import com.devrats.dto.CreateGroupRequest;
import com.devrats.dto.RegisterRequest;
import com.devrats.dto.UpdateChallengeRequest;
import com.devrats.dto.github.GitHubCommit;
import com.devrats.dto.github.GitHubUser;
import com.devrats.entities.ChallengeEntity;
import com.devrats.entities.ChallengeParticipantEntity;
import com.devrats.entities.ChallengeRepositoryEntity;
import com.devrats.entities.GitHubAccountEntity;
import com.devrats.entities.GitHubInstallationEntity;
import com.devrats.entities.GitHubRepositoryEntity;
import com.devrats.entities.UserEntity;
import com.devrats.enums.ChallengeStatus;
import com.devrats.enums.GitHubConnectionStatus;
import com.devrats.enums.GitHubOwnerType;
import com.devrats.enums.RepositoryAccessStatus;
import com.devrats.enums.VerificationMode;
import com.devrats.repositories.ChallengeParticipantRepository;
import com.devrats.repositories.ChallengeRepository;
import com.devrats.repositories.ChallengeRepositoryRepository;
import com.devrats.repositories.ChallengeResultRepository;
import com.devrats.repositories.EligibleCommitRepository;
import com.devrats.repositories.GitHubAccountRepository;
import com.devrats.repositories.GitHubInstallationRepository;
import com.devrats.repositories.GitHubRepositoryRepository;
import com.devrats.repositories.MemberDailyActivityRepository;
import com.devrats.repositories.ReconciliationRunRepository;
import com.devrats.repositories.UserRepository;
import com.devrats.services.AuthService;
import com.devrats.services.AuthSession;
import com.devrats.services.ChallengeService;
import com.devrats.services.GroupService;
import com.devrats.services.ProgressService;
import com.devrats.services.github.GitHubApiClient;
import com.devrats.services.github.GitHubAppTokenProvider;
import com.devrats.services.storage.StudyPhotoStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudyPhotoModeIsolationIntegrationTest extends IntegrationTestBase {

    private static final AtomicLong SEQUENCE = new AtomicLong(20_000);

    @Autowired
    private AuthService authService;
    @Autowired
    private GroupService groupService;
    @Autowired
    private ChallengeService challengeService;
    @Autowired
    private ActivityProcessingService activityProcessingService;
    @Autowired
    private ReconciliationRunner reconciliationRunner;
    @Autowired
    private ProgressService progressService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private GitHubAccountRepository accountRepository;
    @Autowired
    private GitHubInstallationRepository installationRepository;
    @Autowired
    private GitHubRepositoryRepository repositoryRepository;
    @Autowired
    private ChallengeRepository challengeRepository;
    @Autowired
    private ChallengeParticipantRepository participantRepository;
    @Autowired
    private ChallengeRepositoryRepository challengeRepositoryRepository;
    @Autowired
    private MemberDailyActivityRepository activityRepository;
    @Autowired
    private EligibleCommitRepository eligibleCommitRepository;
    @Autowired
    private ChallengeResultRepository resultRepository;
    @Autowired
    private ReconciliationRunRepository reconciliationRunRepository;

    @MockitoBean
    private GitHubApiClient apiClient;
    @MockitoBean
    private GitHubAppTokenProvider tokenProvider;
    @MockitoBean
    private StudyPhotoStorage photoStorage;

    @BeforeEach
    void setUpStorage() {
        when(photoStorage.isAvailable()).thenReturn(true);
    }

    @Test
    void shouldNotCountWebhookCommitInStudyPhotoChallenge() {
        Fixture fixture = photoChallengeFixture();
        when(tokenProvider.getInstallationToken(fixture.installationId())).thenReturn("token");
        when(apiClient.getCommit(eq("token"), eq(fixture.fullName()), anyString()))
                .thenAnswer(invocation -> commit(invocation.getArgument(2), fixture.githubUserId()));

        activityProcessingService.processPush(payload(fixture.externalRepositoryId(), "photo-sha",
                fixture.username(), fixture.fullName()));

        assertNoValidDay(fixture);
        assertThat(eligibleCommitRepository.existsByGithubRepositoryIdAndCommitSha(
                fixture.repositoryId(), "photo-sha")).isTrue();
    }

    @Test
    void shouldNotCountWebhookCommit_whenPhotoChallengeHasLegacyRepositoryLink() {
        Fixture fixture = photoChallengeFixture();
        linkRepository(fixture.challengeId(), fixture.repositoryId());
        when(tokenProvider.getInstallationToken(fixture.installationId())).thenReturn("token");
        when(apiClient.getCommit(eq("token"), eq(fixture.fullName()), anyString()))
                .thenAnswer(invocation -> commit(invocation.getArgument(2), fixture.githubUserId()));

        activityProcessingService.processPush(payload(fixture.externalRepositoryId(), "legacy-link-sha",
                fixture.username(), fixture.fullName()));

        assertNoValidDay(fixture);
        assertThat(challengeRepositoryRepository.countByChallengeId(fixture.challengeId())).isEqualTo(1);
    }

    @Test
    void shouldNotCompleteStudyPhotoChallengeFromCommit() {
        Fixture fixture = photoChallengeFixture(1);
        when(tokenProvider.getInstallationToken(fixture.installationId())).thenReturn("token");
        when(apiClient.getCommit(eq("token"), eq(fixture.fullName()), anyString()))
                .thenAnswer(invocation -> commit(invocation.getArgument(2), fixture.githubUserId()));

        activityProcessingService.processPush(payload(fixture.externalRepositoryId(), "complete-sha",
                fixture.username(), fixture.fullName()));

        ChallengeEntity challenge = challengeRepository.findById(fixture.challengeId()).orElseThrow();
        assertThat(challenge.getStatus()).isEqualTo(ChallengeStatus.ACTIVE);
        assertThat(resultRepository.findByChallengeId(fixture.challengeId())).isEmpty();
        assertNoValidDay(fixture);
    }

    @Test
    void shouldDropLegacyRepositoryLinksWhenStartingPhotoChallenge() {
        UserFixture user = createUser();
        CreateChallengeRequest request = new CreateChallengeRequest(VerificationMode.STUDY_PHOTO, 5,
                Instant.now(), null, null, List.of());
        var group = groupService.createGroup(user.userId(),
                new CreateGroupRequest("Legacy " + user.username(), null, "America/Sao_Paulo", 10, request));
        UUID challengeId = group.currentChallenge().id();
        linkRepository(challengeId, user.repositoryId());

        challengeService.startChallenge(challengeId, user.userId());

        assertThat(challengeRepositoryRepository.countByChallengeId(challengeId)).isZero();
    }

    @Test
    void shouldCountCommitsAfterSwitchingFromStudyPhotoToSelectedRepositories() {
        UserFixture user = createUser();
        CreateChallengeRequest photoRequest = new CreateChallengeRequest(VerificationMode.STUDY_PHOTO, 5,
                Instant.now(), null, null, List.of());
        var group = groupService.createGroup(user.userId(),
                new CreateGroupRequest("Switch " + user.username(), null, "America/Sao_Paulo", 10, photoRequest));
        UUID challengeId = group.currentChallenge().id();
        challengeService.updateChallenge(challengeId, user.userId(),
                new UpdateChallengeRequest(VerificationMode.SELECTED_REPOSITORIES, 5, null, null, null, null,
                        List.of(user.repositoryId())));
        challengeService.startChallenge(challengeId, user.userId());

        when(tokenProvider.getInstallationToken(user.installationId())).thenReturn("token");
        when(apiClient.getCommit(eq("token"), eq(user.fullName()), anyString()))
                .thenAnswer(invocation -> commit(invocation.getArgument(2), user.githubUserId()));
        activityProcessingService.processPush(payload(user.externalRepositoryId(), "selected-sha",
                user.username(), user.fullName()));

        ChallengeParticipantEntity participant = participantRepository
                .findByChallengeIdAndUserId(challengeId, user.userId()).orElseThrow();
        assertThat(participant.getValidDays()).isEqualTo(1);
    }

    @Test
    void shouldIgnoreReconciledCommitInStudyPhotoChallenge() {
        Fixture fixture = photoChallengeFixture();
        ChallengeEntity challenge = challengeRepository.findById(fixture.challengeId()).orElseThrow();
        GitHubRepositoryEntity repository = repositoryRepository.findById(fixture.repositoryId()).orElseThrow();

        activityProcessingService.processReconciledCommit(challenge, repository,
                commit("reconciled-photo-sha", fixture.githubUserId()));

        assertNoValidDay(fixture);
    }

    @Test
    void shouldNotReconcileStudyPhotoChallenge() {
        Fixture fixture = photoChallengeFixture();

        reconciliationRunner.reconcile(fixture.challengeId());

        verify(apiClient, never()).listCommits(anyString(), anyString(), any(Instant.class), anyInt(), anyInt());
        assertThat(reconciliationRunRepository.findAllByChallengeIdOrderByStartedAtDesc(fixture.challengeId()))
                .isEmpty();
    }

    @Test
    void shouldHideCommitEvidenceForStudyPhotoChallenge() {
        Fixture fixture = photoChallengeFixture();
        when(tokenProvider.getInstallationToken(fixture.installationId())).thenReturn("token");
        when(apiClient.getCommit(eq("token"), eq(fixture.fullName()), anyString()))
                .thenAnswer(invocation -> commit(invocation.getArgument(2), fixture.githubUserId()));
        activityProcessingService.processPush(payload(fixture.externalRepositoryId(), "evidence-sha",
                fixture.username(), fixture.fullName()));

        List<?> commits = progressService.listActivityCommits(fixture.challengeId(), fixture.userId(),
                fixture.userId(), LocalDate.now(ZoneId.of("America/Sao_Paulo")), 10);

        assertThat(commits).isEmpty();
    }

    @Test
    void shouldRemoveSelectedRepositoriesWhenSwitchingToStudyPhoto() {
        Fixture fixture = selectedRepositoryFixture();

        assertThat(challengeRepositoryRepository.countByChallengeId(fixture.challengeId())).isEqualTo(1);

        challengeService.updateChallenge(fixture.challengeId(), fixture.userId(),
                new UpdateChallengeRequest(VerificationMode.STUDY_PHOTO, 5, null, null, null, null, null));

        ChallengeEntity updated = challengeRepository.findById(fixture.challengeId()).orElseThrow();
        assertThat(updated.getVerificationMode()).isEqualTo(VerificationMode.STUDY_PHOTO);
        assertThat(challengeRepositoryRepository.countByChallengeId(fixture.challengeId())).isZero();
    }

    private void assertNoValidDay(Fixture fixture) {
        ChallengeParticipantEntity participant = participantRepository
                .findByChallengeIdAndUserId(fixture.challengeId(), fixture.userId()).orElseThrow();
        assertThat(participant.getValidDays()).isZero();
        assertThat(activityRepository.findValidDates(fixture.challengeId(), participant.getId())).isEmpty();
    }

    private Fixture photoChallengeFixture() {
        return photoChallengeFixture(5);
    }

    private Fixture photoChallengeFixture(int targetDays) {
        UserFixture user = createUser();
        CreateChallengeRequest request = new CreateChallengeRequest(VerificationMode.STUDY_PHOTO, targetDays,
                Instant.now(), null, null, List.of());
        UUID challengeId = createGroupAndStartChallenge(user.userId(), "Study " + user.username(), request);
        return new Fixture(user.userId(), user.githubUserId(), user.username(), user.externalRepositoryId(),
                user.installationId(), user.repositoryId(), user.fullName(), challengeId);
    }

    private void linkRepository(UUID challengeId, UUID repositoryId) {
        ChallengeEntity challenge = challengeRepository.findById(challengeId).orElseThrow();
        GitHubRepositoryEntity repository = repositoryRepository.findById(repositoryId).orElseThrow();
        ChallengeRepositoryEntity link = new ChallengeRepositoryEntity();
        link.setChallenge(challenge);
        link.setGithubRepository(repository);
        link.setSlot(1);
        challengeRepositoryRepository.save(link);
    }

    private Fixture selectedRepositoryFixture() {
        UserFixture user = createUser();
        CreateChallengeRequest request = new CreateChallengeRequest(VerificationMode.SELECTED_REPOSITORIES, 5,
                Instant.now(), null, null, List.of(user.repositoryId()));
        //---- updateChallenge only accepts drafts, so the selected-repository challenge is not started.
        var group = groupService.createGroup(user.userId(),
                new CreateGroupRequest("Selected " + user.username(), null, "America/Sao_Paulo", 10, request));
        UUID challengeId = group.currentChallenge().id();
        return new Fixture(user.userId(), user.githubUserId(), user.username(), user.externalRepositoryId(),
                user.installationId(), user.repositoryId(), user.fullName(), challengeId);
    }

    private UUID createGroupAndStartChallenge(UUID userId, String groupName, CreateChallengeRequest request) {
        var group = groupService.createGroup(userId,
                new CreateGroupRequest(groupName, null, "America/Sao_Paulo", 10, request));
        UUID challengeId = group.currentChallenge().id();
        challengeService.startChallenge(challengeId, userId);
        return challengeId;
    }

    private UserFixture createUser() {
        long sequence = SEQUENCE.incrementAndGet();
        String username = "isolation" + sequence;
        AuthSession session = authService.register(new RegisterRequest(
                username + "@test.local", "Str0ng!Passw0rd", "Isolation " + sequence, username,
                "America/Sao_Paulo", "2026-10", null, null, null, null, null));
        UUID userId = session.response().user().id();
        UserEntity user = userRepository.findById(userId).orElseThrow();

        long githubUserId = 30_000L + sequence;
        long installationId = 40_000L + sequence;
        long externalRepositoryId = 50_000L + sequence;
        String fullName = username + "/repo";

        GitHubAccountEntity account = new GitHubAccountEntity();
        account.setUser(user);
        account.setGithubUserId(githubUserId);
        account.setGithubUsername(username);
        account.setConnectionStatus(GitHubConnectionStatus.CONNECTED);
        account.setTokenKeyVersion(1);
        accountRepository.save(account);

        GitHubInstallationEntity installation = new GitHubInstallationEntity();
        installation.setGithubAccount(account);
        installation.setInstallationId(installationId);
        installation.setAccountLogin(username);
        installation.setAccountType("User");
        installationRepository.save(installation);

        GitHubRepositoryEntity repository = new GitHubRepositoryEntity();
        repository.setGithubRepositoryId(externalRepositoryId);
        repository.setOwnerGithubUserId(githubUserId);
        repository.setOwnerLogin(username);
        repository.setOwnerType(GitHubOwnerType.USER);
        repository.setRepositoryName("repo");
        repository.setFullName(fullName);
        repository.setVisibility("PUBLIC");
        repository.setFork(false);
        repository.setArchived(false);
        repository.setInstallationId(installationId);
        repository.setAccessStatus(RepositoryAccessStatus.ACCESSIBLE);
        repositoryRepository.save(repository);

        return new UserFixture(userId, githubUserId, username, externalRepositoryId, installationId,
                repository.getId(), fullName);
    }

    private GitHubCommit commit(String sha, long authorId) {
        Instant now = Instant.now().plusSeconds(1);
        return new GitHubCommit(sha,
                new GitHubCommit.Commit(new GitHubCommit.Author("Dev", "dev@test.local", now),
                        new GitHubCommit.Author("Dev", "dev@test.local", now), "feat: test"),
                new GitHubUser(authorId, "tester", "User", null),
                new GitHubUser(authorId, "tester", "User", null),
                List.of(new GitHubCommit.Parent("parent")),
                new GitHubCommit.Stats(1, 0, 1));
    }

    private String payload(long repositoryId, String sha, String username, String fullName) {
        return """
                {"eventType":"push","repository":{"id":%d,"full_name":"%s"},
                 "commits":[{"id":"%s","timestamp":"%s","author":{"username":"%s"},
                 "committer":{"username":"%s"},"added":1,"removed":0,"modified":0,
                 "parentCount":1,"distinct":true}]}
                """.formatted(repositoryId, fullName, sha, Instant.now().plusSeconds(2), username, username);
    }

    private record UserFixture(UUID userId, long githubUserId, String username, long externalRepositoryId,
                               long installationId, UUID repositoryId, String fullName) {
    }

    private record Fixture(UUID userId, long githubUserId, String username, long externalRepositoryId,
                           long installationId, UUID repositoryId, String fullName, UUID challengeId) {
    }
}

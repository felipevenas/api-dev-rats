package com.devstreak.services.activity;

import com.devstreak.IntegrationTestBase;
import com.devstreak.dto.CreateChallengeRequest;
import com.devstreak.dto.CreateGroupRequest;
import com.devstreak.dto.CreateInviteRequest;
import com.devstreak.dto.RegisterRequest;
import com.devstreak.entities.ChallengeEntity;
import com.devstreak.entities.ChallengeParticipantEntity;
import com.devstreak.entities.GitHubAccountEntity;
import com.devstreak.entities.GitHubRepositoryEntity;
import com.devstreak.entities.UserEntity;
import com.devstreak.enums.ChallengeStatus;
import com.devstreak.enums.GitHubConnectionStatus;
import com.devstreak.enums.GitHubOwnerType;
import com.devstreak.enums.RepositoryAccessStatus;
import com.devstreak.enums.VerificationMode;
import com.devstreak.repositories.ChallengeParticipantRepository;
import com.devstreak.repositories.ChallengeRepository;
import com.devstreak.repositories.ChallengeResultRepository;
import com.devstreak.repositories.GitHubAccountRepository;
import com.devstreak.repositories.GitHubRepositoryRepository;
import com.devstreak.repositories.MemberDailyActivityRepository;
import com.devstreak.repositories.UserRepository;
import com.devstreak.services.AuthService;
import com.devstreak.services.AuthSession;
import com.devstreak.services.ChallengeService;
import com.devstreak.services.GroupService;
import com.devstreak.services.InviteService;
import com.devstreak.services.github.GitHubApiClient;
import com.devstreak.services.github.GitHubAppTokenProvider;
import com.devstreak.dto.github.GitHubCommit;
import com.devstreak.dto.github.GitHubUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class ActivityProcessingIntegrationTest extends IntegrationTestBase {

    private static final AtomicLong GITHUB_IDS = new AtomicLong(10_000);

    @Autowired
    private AuthService authService;
    @Autowired
    private GroupService groupService;
    @Autowired
    private ChallengeService challengeService;
    @Autowired
    private InviteService inviteService;
    @Autowired
    private ActivityProcessingService activityProcessingService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private GitHubAccountRepository accountRepository;
    @Autowired
    private GitHubRepositoryRepository repositoryRepository;
    @Autowired
    private ChallengeRepository challengeRepository;
    @Autowired
    private ChallengeParticipantRepository participantRepository;
    @Autowired
    private ChallengeResultRepository resultRepository;
    @Autowired
    private MemberDailyActivityRepository activityRepository;

    @MockitoBean
    private GitHubApiClient apiClient;
    @MockitoBean
    private GitHubAppTokenProvider tokenProvider;

    @Test
    void shouldCountOnlyOneValidDay_whenTwoCommitsHappenOnSameDay() {
        Fixture fixture = fixture(5);

        when(tokenProvider.getInstallationToken(fixture.installationId())).thenReturn("token");
        when(apiClient.getCommit(eq("token"), eq("tester/repo"), anyString()))
                .thenAnswer(invocation -> commit(invocation.getArgument(2), fixture.githubUserId()));

        activityProcessingService.processPush(payload(fixture.repositoryId(), "sha-1", fixture.githubUsername()));
        activityProcessingService.processPush(payload(fixture.repositoryId(), "sha-2", fixture.githubUsername()));

        ChallengeParticipantEntity participant = participantRepository
                .findByChallengeIdAndUserId(fixture.challengeId(), fixture.userId()).orElseThrow();
        assertThat(participant.getValidDays()).isEqualTo(1);
        assertThat(activityRepository.findValidDates(fixture.challengeId(), participant.getId())).hasSize(1);
        assertThat(activityRepository.findAllByChallengeIdAndParticipantIdAndValidationStatusOrderByActivityDateAsc(
                fixture.challengeId(), participant.getId(), com.devstreak.enums.ActivityStatus.VALID))
                .singleElement()
                .satisfies(day -> assertThat(day.getValidCommitCount()).isEqualTo(2));
    }

    @Test
    void shouldDeclareSingleWinner_whenParticipantReachesTarget() {
        Fixture fixture = fixture(1);

        when(tokenProvider.getInstallationToken(fixture.installationId())).thenReturn("token");
        when(apiClient.getCommit(eq("token"), eq("tester/repo"), anyString()))
                .thenAnswer(invocation -> commit(invocation.getArgument(2), fixture.githubUserId()));

        activityProcessingService.processPush(payload(fixture.repositoryId(), "winner-sha", fixture.githubUsername()));

        ChallengeEntity challenge = challengeRepository.findById(fixture.challengeId()).orElseThrow();
        assertThat(challenge.getStatus()).isEqualTo(ChallengeStatus.COMPLETED);
        assertThat(resultRepository.findByChallengeId(fixture.challengeId())).isPresent();
    }

    @Test
    void shouldDeclareExactlyOneWinner_whenTwoParticipantsCompleteConcurrently() throws Exception {
        long githubIdA = GITHUB_IDS.incrementAndGet();
        long githubIdB = GITHUB_IDS.incrementAndGet();
        ParticipantFixture a = participantFixture(githubIdA);
        ParticipantFixture b = participantFixture(githubIdB);

        CreateChallengeRequest challengeRequest = new CreateChallengeRequest(VerificationMode.OWN_REPOSITORIES,
                1, Instant.now(), null, null, List.of());
        var group = groupService.createGroup(a.userId(), new CreateGroupRequest("Concurrent Group", null,
                "America/Sao_Paulo", 10, challengeRequest));
        String inviteToken = inviteService.createInvite(group.id(), a.userId(),
                new CreateInviteRequest(null, null)).token();
        inviteService.accept(inviteToken, b.userId());
        challengeService.startChallenge(group.currentChallenge().id(), a.userId());

        when(tokenProvider.getInstallationToken(a.installationId())).thenReturn("token-a");
        when(tokenProvider.getInstallationToken(b.installationId())).thenReturn("token-b");
        when(apiClient.getCommit(eq("token-a"), eq(a.fullName()), anyString()))
                .thenAnswer(invocation -> commit(invocation.getArgument(2), githubIdA));
        when(apiClient.getCommit(eq("token-b"), eq(b.fullName()), anyString()))
                .thenAnswer(invocation -> commit(invocation.getArgument(2), githubIdB));

        String payloadA = payload(a.externalRepositoryId(), "race-a", a.username());
        String payloadB = payload(b.externalRepositoryId(), "race-b", b.username());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> futureA = pool.submit(() -> {
                start.await();
                activityProcessingService.processPush(payloadA);
                return null;
            });
            Future<?> futureB = pool.submit(() -> {
                start.await();
                activityProcessingService.processPush(payloadB);
                return null;
            });
            start.countDown();
            futureA.get(30, TimeUnit.SECONDS);
            futureB.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        ChallengeEntity challenge = challengeRepository.findById(group.currentChallenge().id()).orElseThrow();
        assertThat(challenge.getStatus()).isEqualTo(ChallengeStatus.COMPLETED);
        long results = resultRepository.findAll().stream()
                .filter(result -> result.getChallenge().getId().equals(challenge.getId()))
                .count();
        assertThat(results).isEqualTo(1);
    }

    private ParticipantFixture participantFixture(long githubId) {
        String username = "user" + githubId;
        AuthSession session = authService.register(new RegisterRequest(
                username + "@test.local", "Str0ng!Passw0rd", "Activity " + githubId, username,
                "America/Sao_Paulo", "2026-10"));
        UUID userId = session.response().user().id();
        UserEntity user = userRepository.findById(userId).orElseThrow();

        GitHubAccountEntity account = new GitHubAccountEntity();
        account.setUser(user);
        account.setGithubUserId(githubId);
        account.setGithubUsername(username);
        account.setConnectionStatus(GitHubConnectionStatus.CONNECTED);
        account.setTokenKeyVersion(1);
        accountRepository.save(account);

        long externalRepositoryId = githubId + 3_000_000;
        String fullName = username + "/repo";
        GitHubRepositoryEntity repository = new GitHubRepositoryEntity();
        repository.setGithubRepositoryId(externalRepositoryId);
        repository.setOwnerGithubUserId(githubId);
        repository.setOwnerLogin(username);
        repository.setOwnerType(GitHubOwnerType.USER);
        repository.setRepositoryName("repo");
        repository.setFullName(fullName);
        repository.setVisibility("PUBLIC");
        repository.setFork(false);
        repository.setArchived(false);
        repository.setInstallationId(githubId + 4_000_000);
        repository.setAccessStatus(RepositoryAccessStatus.ACCESSIBLE);
        repositoryRepository.save(repository);
        return new ParticipantFixture(userId, username, externalRepositoryId, repository.getInstallationId(), fullName);
    }

    private Fixture fixture(int targetDays) {
        long githubId = GITHUB_IDS.incrementAndGet();
        String username = "user" + githubId;
        AuthSession session = authService.register(new RegisterRequest(
                username + "@test.local", "Str0ng!Passw0rd", "Activity " + githubId, username,
                "America/Sao_Paulo", "2026-10"));
        UUID userId = session.response().user().id();
        UserEntity user = userRepository.findById(userId).orElseThrow();

        GitHubAccountEntity account = new GitHubAccountEntity();
        account.setUser(user);
        account.setGithubUserId(githubId);
        account.setGithubUsername(username);
        account.setConnectionStatus(GitHubConnectionStatus.CONNECTED);
        account.setTokenKeyVersion(1);
        accountRepository.save(account);

        long externalRepositoryId = githubId + 1_000_000;
        GitHubRepositoryEntity repository = new GitHubRepositoryEntity();
        repository.setGithubRepositoryId(externalRepositoryId);
        repository.setOwnerGithubUserId(githubId);
        repository.setOwnerLogin(username);
        repository.setOwnerType(GitHubOwnerType.USER);
        repository.setRepositoryName("repo");
        repository.setFullName("tester/repo");
        repository.setVisibility("PUBLIC");
        repository.setFork(false);
        repository.setArchived(false);
        repository.setInstallationId(githubId + 2_000_000);
        repository.setAccessStatus(RepositoryAccessStatus.ACCESSIBLE);
        repositoryRepository.save(repository);

        CreateChallengeRequest challengeRequest = new CreateChallengeRequest(VerificationMode.OWN_REPOSITORIES,
                targetDays, Instant.now(), null, null, List.of());
        var group = groupService.createGroup(userId, new CreateGroupRequest("Group " + githubId, null,
                "America/Sao_Paulo", 10, challengeRequest));
        UUID challengeId = group.currentChallenge().id();
        challengeService.startChallenge(challengeId, userId);

        return new Fixture(userId, githubId, username, externalRepositoryId, repository.getInstallationId(), challengeId);
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

    private String payload(long repositoryId, String sha, String username) {
        return """
                {"eventType":"push","repository":{"id":%d,"full_name":"tester/repo"},
                 "commits":[{"id":"%s","timestamp":"%s","author":{"username":"%s"},
                 "committer":{"username":"%s"},"added":1,"removed":0,"modified":0,
                 "parentCount":1,"distinct":true}]}
                """.formatted(repositoryId, sha, Instant.now().plusSeconds(2), username, username);
    }

    private record Fixture(UUID userId, long githubUserId, String githubUsername, long repositoryId,
                           long installationId, UUID challengeId) {
    }

    private record ParticipantFixture(UUID userId, String username, long externalRepositoryId,
                                      long installationId, String fullName) {
    }
}

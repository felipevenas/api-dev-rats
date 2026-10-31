package com.devrats.controllers;

import com.devrats.IntegrationTestBase;
import com.devrats.dto.RegisterRequest;
import com.devrats.entities.ChallengeEntity;
import com.devrats.entities.ChallengeParticipantEntity;
import com.devrats.entities.ChallengeRepositoryEntity;
import com.devrats.entities.GitHubAccountEntity;
import com.devrats.entities.GitHubRepositoryEntity;
import com.devrats.entities.GroupEntity;
import com.devrats.entities.GroupMemberEntity;
import com.devrats.entities.UserEntity;
import com.devrats.enums.ChallengeStatus;
import com.devrats.enums.EligibilityStatus;
import com.devrats.enums.GitHubConnectionStatus;
import com.devrats.enums.GitHubOwnerType;
import com.devrats.enums.GroupRole;
import com.devrats.enums.MemberStatus;
import com.devrats.enums.ParticipantStatus;
import com.devrats.enums.RepositoryAccessStatus;
import com.devrats.enums.VerificationMode;
import com.devrats.repositories.ChallengeParticipantRepository;
import com.devrats.repositories.ChallengeRepository;
import com.devrats.repositories.ChallengeRepositoryRepository;
import com.devrats.repositories.EligibleCommitRepository;
import com.devrats.repositories.GitHubAccountRepository;
import com.devrats.repositories.GitHubRepositoryRepository;
import com.devrats.repositories.GroupMemberRepository;
import com.devrats.repositories.GroupRepository;
import com.devrats.repositories.UserRepository;
import com.devrats.services.AuthService;
import com.devrats.services.AuthSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class ActivityCommitsIntegrationTest extends IntegrationTestBase {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final AtomicLong GITHUB_IDS = new AtomicLong(120_000);

    @Autowired
    private AuthService authService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private GroupRepository groupRepository;
    @Autowired
    private GroupMemberRepository memberRepository;
    @Autowired
    private ChallengeRepository challengeRepository;
    @Autowired
    private ChallengeParticipantRepository participantRepository;
    @Autowired
    private ChallengeRepositoryRepository challengeRepositoryRepository;
    @Autowired
    private GitHubAccountRepository accountRepository;
    @Autowired
    private GitHubRepositoryRepository repositoryRepository;
    @Autowired
    private EligibleCommitRepository eligibleCommitRepository;

    @Test
    void shouldListEligibleCommitsOfOwnRepositoriesForTheDay() throws Exception {
        Fixture fixture = fixture(VerificationMode.OWN_REPOSITORIES, false);
        GitHubRepositoryEntity repository = repository(fixture.githubId(), fixture.githubUsername(), "own-repo");

        Instant committedAt = Instant.now().minus(1, ChronoUnit.MINUTES);
        insertCommit(repository, "sha-own-1", fixture.githubId(), fixture.githubUsername(), committedAt,
                EligibilityStatus.ELIGIBLE);
        insertCommit(repository, "sha-own-2", fixture.githubId(), fixture.githubUsername(),
                committedAt.plusSeconds(30), EligibilityStatus.ELIGIBLE);
        insertCommit(repository, "sha-ineligible", fixture.githubId(), fixture.githubUsername(),
                committedAt.plusSeconds(60), EligibilityStatus.INELIGIBLE);

        LocalDate date = LocalDate.ofInstant(committedAt, ZONE);

        mockMvc.perform(get(url(fixture.challenge(), date)).with(authenticatedAs(fixture.owner().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].sha").value("sha-own-1"))
                .andExpect(jsonPath("$[1].sha").value("sha-own-2"))
                .andExpect(jsonPath("$[0].repositoryFullName").value(repository.getFullName()))
                .andExpect(jsonPath("$[0].commitUrl").value(
                        "https://github.com/" + repository.getFullName() + "/commit/sha-own-1"))
                .andExpect(jsonPath("$[0].committedAt").exists())
                .andExpect(jsonPath("$[0].authorLogin").value(fixture.githubUsername()))
                //---- Mensagem e código nunca são expostos.
                .andExpect(jsonPath("$[0].commitMessage").doesNotExist())
                .andExpect(jsonPath("$[0].message").doesNotExist());
    }

    @Test
    void shouldIgnoreOwnRepositoriesThatAreNotEligibleForTheAuthor() throws Exception {
        Fixture fixture = fixture(VerificationMode.OWN_REPOSITORIES, false);
        Instant committedAt = Instant.now().minus(1, ChronoUnit.MINUTES);
        LocalDate date = LocalDate.ofInstant(committedAt, ZONE);

        GitHubRepositoryEntity foreignRepository = repository(GITHUB_IDS.incrementAndGet(), "someone-else", "foreign");
        insertCommit(foreignRepository, "sha-foreign", fixture.githubId(), fixture.githubUsername(), committedAt,
                EligibilityStatus.ELIGIBLE);

        GitHubRepositoryEntity archivedRepository = repository(fixture.githubId(), fixture.githubUsername(), "archived");
        archivedRepository.setArchived(true);
        repositoryRepository.save(archivedRepository);
        insertCommit(archivedRepository, "sha-archived", fixture.githubId(), fixture.githubUsername(), committedAt,
                EligibilityStatus.ELIGIBLE);

        GitHubRepositoryEntity forkRepository = repository(fixture.githubId(), fixture.githubUsername(), "fork");
        forkRepository.setFork(true);
        repositoryRepository.save(forkRepository);
        insertCommit(forkRepository, "sha-fork", fixture.githubId(), fixture.githubUsername(), committedAt,
                EligibilityStatus.ELIGIBLE);

        GitHubRepositoryEntity validRepository = repository(fixture.githubId(), fixture.githubUsername(), "valid");
        insertCommit(validRepository, "sha-valid", fixture.githubId(), fixture.githubUsername(), committedAt,
                EligibilityStatus.ELIGIBLE);

        mockMvc.perform(get(url(fixture.challenge(), date)).with(authenticatedAs(fixture.owner().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sha").value("sha-valid"));
    }

    @Test
    void shouldAllowForkedOwnRepositoryWhenChallengeAllowsForks() throws Exception {
        Fixture fixture = fixture(VerificationMode.OWN_REPOSITORIES, true);
        Instant committedAt = Instant.now().minus(1, ChronoUnit.MINUTES);
        LocalDate date = LocalDate.ofInstant(committedAt, ZONE);

        GitHubRepositoryEntity fork = repository(fixture.githubId(), fixture.githubUsername(), "allowed-fork");
        fork.setFork(true);
        repositoryRepository.save(fork);
        insertCommit(fork, "sha-fork", fixture.githubId(), fixture.githubUsername(), committedAt,
                EligibilityStatus.ELIGIBLE);

        mockMvc.perform(get(url(fixture.challenge(), date)).with(authenticatedAs(fixture.owner().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sha").value("sha-fork"));
    }

    @Test
    void shouldListOnlyLinkedRepositoriesInSelectedMode() throws Exception {
        Fixture fixture = fixture(VerificationMode.SELECTED_REPOSITORIES, false);
        GitHubRepositoryEntity linked = repository(fixture.githubId(), fixture.githubUsername(), "linked");
        GitHubRepositoryEntity notLinked = repository(fixture.githubId(), fixture.githubUsername(), "not-linked");
        link(fixture.challenge(), linked, 1);

        Instant committedAt = Instant.now().minus(1, ChronoUnit.MINUTES);
        LocalDate date = LocalDate.ofInstant(committedAt, ZONE);
        insertCommit(linked, "sha-linked", fixture.githubId(), fixture.githubUsername(), committedAt,
                EligibilityStatus.ELIGIBLE);
        insertCommit(notLinked, "sha-not-linked", fixture.githubId(), fixture.githubUsername(), committedAt,
                EligibilityStatus.ELIGIBLE);

        mockMvc.perform(get(url(fixture.challenge(), date)).with(authenticatedAs(fixture.owner().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sha").value("sha-linked"));
    }

    @Test
    void shouldIgnoreCommitsAuthoredBySomeoneElse() throws Exception {
        Fixture fixture = fixture(VerificationMode.OWN_REPOSITORIES, false);
        GitHubRepositoryEntity repository = repository(fixture.githubId(), fixture.githubUsername(), "own-repo");

        Instant committedAt = Instant.now().minus(1, ChronoUnit.MINUTES);
        LocalDate date = LocalDate.ofInstant(committedAt, ZONE);
        insertCommit(repository, "sha-other-author", GITHUB_IDS.incrementAndGet(), "other-author", committedAt,
                EligibilityStatus.ELIGIBLE);
        insertCommit(repository, "sha-own-author", fixture.githubId(), fixture.githubUsername(),
                committedAt.plusSeconds(10), EligibilityStatus.ELIGIBLE);

        mockMvc.perform(get(url(fixture.challenge(), date)).with(authenticatedAs(fixture.owner().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sha").value("sha-own-author"));
    }

    @Test
    void shouldIgnoreCommitsOutsideTheRequestedDay() throws Exception {
        Fixture fixture = fixture(VerificationMode.OWN_REPOSITORIES, false);
        GitHubRepositoryEntity repository = repository(fixture.githubId(), fixture.githubUsername(), "own-repo");

        Instant committedAt = Instant.now().minus(1, ChronoUnit.MINUTES);
        LocalDate activityDate = LocalDate.ofInstant(committedAt, ZONE);
        insertCommit(repository, "sha-today", fixture.githubId(), fixture.githubUsername(), committedAt,
                EligibilityStatus.ELIGIBLE);

        mockMvc.perform(get(url(fixture.challenge(), activityDate.minusDays(1)))
                        .with(authenticatedAs(fixture.owner().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldReturnEmptyListWhenDayHasNoCommits() throws Exception {
        Fixture fixture = fixture(VerificationMode.OWN_REPOSITORIES, false);
        repository(fixture.githubId(), fixture.githubUsername(), "own-repo");

        mockMvc.perform(get(url(fixture.challenge(), LocalDate.now(ZONE)))
                        .with(authenticatedAs(fixture.owner().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldResolveAnotherParticipantThroughUserId() throws Exception {
        Fixture fixture = fixture(VerificationMode.OWN_REPOSITORIES, false);
        Participant second = join(fixture, "second");
        GitHubRepositoryEntity secondRepository = repository(second.githubId(), second.githubUsername(), "second-repo");
        GitHubRepositoryEntity ownerRepository = repository(fixture.githubId(), fixture.githubUsername(), "owner-repo");

        Instant committedAt = Instant.now().minus(1, ChronoUnit.MINUTES);
        LocalDate date = LocalDate.ofInstant(committedAt, ZONE);
        insertCommit(secondRepository, "sha-second", second.githubId(), second.githubUsername(), committedAt,
                EligibilityStatus.ELIGIBLE);
        insertCommit(ownerRepository, "sha-owner", fixture.githubId(), fixture.githubUsername(), committedAt,
                EligibilityStatus.ELIGIBLE);

        mockMvc.perform(get(url(fixture.challenge(), date))
                        .param("userId", second.user().getId().toString())
                        .with(authenticatedAs(fixture.owner().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sha").value("sha-second"));
    }

    @Test
    void shouldDenyAccessForNonMember() throws Exception {
        Fixture fixture = fixture(VerificationMode.OWN_REPOSITORIES, false);
        UserEntity outsider = register("outsider");

        mockMvc.perform(get(url(fixture.challenge(), LocalDate.now(ZONE)))
                        .with(authenticatedAs(outsider.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
    }

    private String url(ChallengeEntity challenge, LocalDate date) {
        return "/api/v1/challenges/" + challenge.getId() + "/activity/" + date + "/commits";
    }

    private Fixture fixture(VerificationMode mode, boolean allowForks) {
        UserEntity owner = register("owner");
        long githubId = GITHUB_IDS.incrementAndGet();
        String githubUsername = connectGithub(owner, githubId);

        GroupEntity group = new GroupEntity();
        group.setName("Activity commits " + githubId);
        group.setCreator(owner);
        group.setTimezone("America/Sao_Paulo");
        group.setMaxMembers(10);
        groupRepository.save(group);

        GroupMemberEntity member = joinGroup(group, owner, GroupRole.OWNER);

        Instant startsAt = Instant.now().minus(5, ChronoUnit.DAYS);
        ChallengeEntity challenge = new ChallengeEntity();
        challenge.setGroup(group);
        challenge.setSequenceNo(1);
        challenge.setVerificationMode(mode);
        challenge.setTargetValidDays(30);
        challenge.setTimezone("America/Sao_Paulo");
        challenge.setStatus(ChallengeStatus.ACTIVE);
        challenge.setStartsAt(startsAt);
        challenge.setAllowForks(allowForks);
        challenge.setTiebreakRule("test");
        challenge.setCreatedBy(owner);
        challengeRepository.save(challenge);

        ChallengeParticipantEntity participant = new ChallengeParticipantEntity();
        participant.setChallenge(challenge);
        participant.setUser(owner);
        participant.setGroupMember(member);
        participant.setStatus(ParticipantStatus.ACTIVE);
        participantRepository.saveAndFlush(participant);
        //---- joinedAt é preenchido no insert; ajusta para a janela do teste.
        participant.setJoinedAt(startsAt);
        participantRepository.saveAndFlush(participant);

        return new Fixture(owner, group, challenge, githubId, githubUsername);
    }

    private Participant join(Fixture fixture, String prefix) {
        UserEntity user = register(prefix);
        long githubId = GITHUB_IDS.incrementAndGet();
        String githubUsername = connectGithub(user, githubId);
        GroupMemberEntity member = joinGroup(fixture.group(), user, GroupRole.MEMBER);

        ChallengeParticipantEntity participant = new ChallengeParticipantEntity();
        participant.setChallenge(fixture.challenge());
        participant.setUser(user);
        participant.setGroupMember(member);
        participant.setStatus(ParticipantStatus.ACTIVE);
        participantRepository.saveAndFlush(participant);
        participant.setJoinedAt(fixture.challenge().getStartsAt());
        participantRepository.saveAndFlush(participant);

        return new Participant(user, githubId, githubUsername);
    }

    private GroupMemberEntity joinGroup(GroupEntity group, UserEntity user, GroupRole role) {
        GroupMemberEntity member = new GroupMemberEntity();
        member.setGroup(group);
        member.setUser(user);
        member.setRole(role);
        member.setStatus(MemberStatus.ACTIVE);
        return memberRepository.save(member);
    }

    private UserEntity register(String prefix) {
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        AuthSession session = authService.register(RegisterRequest.basic(
                prefix + "-" + random + "@test.local", "Str0ng!Passw0rd", "User " + random,
                prefix + random, "America/Sao_Paulo", "2026-10"));
        return userRepository.findById(session.response().user().id()).orElseThrow();
    }

    private String connectGithub(UserEntity user, long githubUserId) {
        String username = "gh" + githubUserId;
        GitHubAccountEntity account = new GitHubAccountEntity();
        account.setUser(user);
        account.setGithubUserId(githubUserId);
        account.setGithubUsername(username);
        account.setConnectionStatus(GitHubConnectionStatus.CONNECTED);
        account.setTokenKeyVersion(1);
        accountRepository.save(account);
        return username;
    }

    private GitHubRepositoryEntity repository(long ownerGithubId, String ownerLogin, String name) {
        GitHubRepositoryEntity repository = new GitHubRepositoryEntity();
        repository.setGithubRepositoryId(GITHUB_IDS.incrementAndGet());
        repository.setOwnerGithubUserId(ownerGithubId);
        repository.setOwnerLogin(ownerLogin);
        repository.setOwnerType(GitHubOwnerType.USER);
        repository.setRepositoryName(name);
        repository.setFullName(ownerLogin + "/" + name);
        repository.setVisibility("PUBLIC");
        repository.setFork(false);
        repository.setArchived(false);
        repository.setAccessStatus(RepositoryAccessStatus.ACCESSIBLE);
        return repositoryRepository.save(repository);
    }

    private void link(ChallengeEntity challenge, GitHubRepositoryEntity repository, int slot) {
        ChallengeRepositoryEntity link = new ChallengeRepositoryEntity();
        link.setChallenge(challenge);
        link.setGithubRepository(repository);
        link.setSlot(slot);
        challengeRepositoryRepository.save(link);
    }

    private void insertCommit(GitHubRepositoryEntity repository, String sha, long authorId, String authorLogin,
                              Instant committedAt, EligibilityStatus status) {
        eligibleCommitRepository.insertIfAbsent(repository.getId(), sha, authorId, authorId, authorLogin,
                committedAt, Instant.now(), false, status.name(),
                status == EligibilityStatus.ELIGIBLE ? null : "TEST_INELIGIBLE", "WEBHOOK");
    }

    private record Fixture(UserEntity owner, GroupEntity group, ChallengeEntity challenge, long githubId,
                           String githubUsername) {
    }

    private record Participant(UserEntity user, long githubId, String githubUsername) {
    }
}

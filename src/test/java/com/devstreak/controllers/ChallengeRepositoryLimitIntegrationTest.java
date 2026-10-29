package com.devstreak.controllers;

import com.devstreak.IntegrationTestBase;
import com.devstreak.dto.RegisterRequest;
import com.devstreak.entities.GitHubAccountEntity;
import com.devstreak.entities.GitHubInstallationEntity;
import com.devstreak.entities.GitHubRepositoryEntity;
import com.devstreak.entities.UserEntity;
import com.devstreak.enums.GitHubConnectionStatus;
import com.devstreak.enums.GitHubOwnerType;
import com.devstreak.enums.RepositoryAccessStatus;
import com.devstreak.repositories.GitHubAccountRepository;
import com.devstreak.repositories.GitHubInstallationRepository;
import com.devstreak.repositories.GitHubRepositoryRepository;
import com.devstreak.repositories.UserRepository;
import com.devstreak.services.AuthService;
import com.devstreak.services.AuthSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChallengeRepositoryLimitIntegrationTest extends IntegrationTestBase {

    @Autowired
    private AuthService authService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private GitHubAccountRepository accountRepository;
    @Autowired
    private GitHubInstallationRepository installationRepository;
    @Autowired
    private GitHubRepositoryRepository repositoryRepository;

    @Test
    void shouldRejectFourthRepository() throws Exception {
        AuthSession session = register();
        UUID userId = session.response().user().id();
        long installationId = 90_001L;
        List<UUID> repositoryIds = createRepositories(userId, installationId, 4);

        String body = """
                {"name":"Repo limit","description":null,"timezone":"America/Sao_Paulo","maxMembers":10,
                 "challenge":{"verificationMode":"SELECTED_REPOSITORIES","targetValidDays":5,
                 "startsAt":"%s","repositoryIds":["%s","%s","%s"]}}
                """.formatted(Instant.now(), repositoryIds.get(0), repositoryIds.get(1), repositoryIds.get(2));

        var result = mockMvc.perform(post("/api/v1/groups")
                        .with(authenticatedAs(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentChallenge.repositories.length()").value(3))
                .andReturn();

        String challengeId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("currentChallenge").path("id").asString();

        mockMvc.perform(post("/api/v1/challenges/" + challengeId + "/repositories")
                        .with(authenticatedAs(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"githubRepositoryId\":\"" + repositoryIds.get(3) + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPOSITORY_LIMIT_REACHED"));
    }

    private AuthSession register() {
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return authService.register(new RegisterRequest("limit-" + random + "@test.local",
                "Str0ng!Passw0rd", "Repo Limit", "limit" + random, "America/Sao_Paulo", "2026-10"));
    }

    private List<UUID> createRepositories(UUID userId, long installationId, int count) {
        UserEntity user = userRepository.findById(userId).orElseThrow();
        GitHubAccountEntity account = new GitHubAccountEntity();
        account.setUser(user);
        account.setGithubUserId(77_001L);
        account.setGithubUsername("limit-user");
        account.setConnectionStatus(GitHubConnectionStatus.CONNECTED);
        account.setTokenKeyVersion(1);
        accountRepository.save(account);

        GitHubInstallationEntity installation = new GitHubInstallationEntity();
        installation.setGithubAccount(account);
        installation.setInstallationId(installationId);
        installation.setAccountLogin("limit-user");
        installation.setAccountType("User");
        installationRepository.save(installation);

        List<UUID> ids = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            GitHubRepositoryEntity repository = new GitHubRepositoryEntity();
            repository.setGithubRepositoryId(88_000L + index);
            repository.setOwnerGithubUserId(77_001L);
            repository.setOwnerLogin("limit-user");
            repository.setOwnerType(GitHubOwnerType.USER);
            repository.setRepositoryName("repo" + index);
            repository.setFullName("limit-user/repo" + index);
            repository.setVisibility("PUBLIC");
            repository.setFork(false);
            repository.setArchived(false);
            repository.setInstallationId(installationId);
            repository.setAccessStatus(RepositoryAccessStatus.ACCESSIBLE);
            repositoryRepository.save(repository);
            ids.add(repository.getId());
        }
        return ids;
    }
}

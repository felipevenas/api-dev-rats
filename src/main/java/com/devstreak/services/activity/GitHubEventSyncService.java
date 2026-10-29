package com.devstreak.services.activity;

import com.devstreak.entities.GitHubEventEntity;
import com.devstreak.entities.GitHubInstallationEntity;
import com.devstreak.entities.GitHubRepositoryEntity;
import com.devstreak.enums.RepositoryAccessStatus;
import com.devstreak.repositories.GitHubInstallationRepository;
import com.devstreak.repositories.GitHubRepositoryRepository;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.Set;

@Service
public class GitHubEventSyncService {

    private static final Set<String> REPOSITORY_REVOKE_ACTIONS = Set.of("deleted", "transferred");

    private final GitHubRepositoryRepository repositoryRepository;
    private final GitHubInstallationRepository installationRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public GitHubEventSyncService(GitHubRepositoryRepository repositoryRepository,
                                  GitHubInstallationRepository installationRepository,
                                  ObjectMapper objectMapper,
                                  Clock clock) {
        this.repositoryRepository = repositoryRepository;
        this.installationRepository = installationRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void handle(GitHubEventEntity event) {
        JsonNode root = objectMapper.readTree(event.getPayload());
        switch (event.getEventType()) {
            case "repository" -> handleRepository(root);
            case "installation" -> handleInstallation(root);
            default -> {
                //---- installation_repositories is refreshed by the next repository sync.
            }
        }
    }

    private void handleRepository(JsonNode root) {
        long repositoryId = root.path("repository").path("id").asLong(0);
        String action = root.path("action").asString("");
        if (repositoryId == 0) {
            return;
        }
        repositoryRepository.findByGithubRepositoryId(repositoryId).ifPresent(repository -> {
            if (REPOSITORY_REVOKE_ACTIONS.contains(action)) {
                repository.setAccessStatus(RepositoryAccessStatus.REVOKED);
                repositoryRepository.save(repository);
            }
        });
    }

    private void handleInstallation(JsonNode root) {
        long installationId = root.path("installation").path("id").asLong(0);
        String action = root.path("action").asString("");
        if (installationId == 0) {
            return;
        }
        installationRepository.findByInstallationId(installationId).ifPresent(installation -> {
            if ("suspend".equals(action)) {
                installation.setSuspendedAt(clock.instant());
            } else if ("unsuspend".equals(action)) {
                installation.setSuspendedAt(null);
            }
            installationRepository.save(installation);
        });
    }
}

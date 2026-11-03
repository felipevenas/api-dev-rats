package com.devrats.services.activity;

import com.devrats.entities.GitHubEventEntity;
import com.devrats.entities.GitHubInstallationEntity;
import com.devrats.entities.GitHubRepositoryEntity;
import com.devrats.enums.GitHubOwnerType;
import com.devrats.enums.RepositoryAccessStatus;
import com.devrats.repositories.GitHubInstallationRepository;
import com.devrats.repositories.GitHubRepositoryRepository;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.List;
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
            case "installation" -> handleInstallation(root, root.path("action").asString(""));
            case "installation_suspend" -> handleInstallation(root, "suspend");
            case "installation_unsuspend" -> handleInstallation(root, "unsuspend");
            case "installation_repositories" -> handleInstallationRepositories(root);
            default -> {
                //---- Only the events above change local installation or repository access.
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

    private void handleInstallation(JsonNode root, String action) {
        Long installationId = readInstallationId(root);
        if (installationId == null) {
            return;
        }
        List<GitHubInstallationEntity> installations = installationRepository.findAllByInstallationId(installationId);
        for (GitHubInstallationEntity installation : installations) {
            switch (action) {
                case "suspend", "deleted" -> installation.setSuspendedAt(clock.instant());
                case "unsuspend", "created" -> installation.setSuspendedAt(null);
                default -> {
                    continue;
                }
            }
            installationRepository.save(installation);
        }
        //---- The installation no longer exists on GitHub, so every repository it exposed is revoked.
        if ("deleted".equals(action)) {
            revokeRepositories(installationId);
        }
    }

    private void handleInstallationRepositories(JsonNode root) {
        Long installationId = readInstallationId(root);
        if (installationId == null) {
            return;
        }
        JsonNode added = root.path("repositories_added");
        if (added.isArray()) {
            added.forEach(repository -> upsertRepository(repository, installationId));
        }
        JsonNode removed = root.path("repositories_removed");
        if (removed.isArray()) {
            removed.forEach(repository -> revokeRepository(repository.path("id").asLong(0), installationId));
        }
    }

    private void upsertRepository(JsonNode node, Long installationId) {
        long repositoryId = node.path("id").asLong(0);
        if (repositoryId == 0) {
            return;
        }
        GitHubRepositoryEntity repository = repositoryRepository.findByGithubRepositoryId(repositoryId)
                .orElseGet(GitHubRepositoryEntity::new);
        JsonNode owner = node.path("owner");
        repository.setGithubRepositoryId(repositoryId);
        repository.setInstallationId(installationId);
        repository.setRepositoryName(node.path("name").asString(""));
        repository.setFullName(node.path("full_name").asString(""));
        repository.setVisibility(node.path("private").asBoolean(false) ? "PRIVATE" : "PUBLIC");
        repository.setFork(node.path("fork").asBoolean(false));
        repository.setArchived(node.path("archived").asBoolean(false));
        String ownerLogin = owner.path("login").asString("");
        repository.setOwnerGithubUserId(owner.path("id").isNumber() ? owner.path("id").asLong() : null);
        repository.setOwnerLogin(ownerLogin.isBlank() ? "unknown" : ownerLogin);
        repository.setOwnerType("Organization".equalsIgnoreCase(owner.path("type").asString(""))
                ? GitHubOwnerType.ORGANIZATION : GitHubOwnerType.USER);
        repository.setAccessStatus(RepositoryAccessStatus.ACCESSIBLE);
        repository.setLastSyncedAt(clock.instant());
        repositoryRepository.save(repository);
    }

    private void revokeRepository(long repositoryId, Long installationId) {
        if (repositoryId == 0) {
            return;
        }
        //---- A repository that already belongs to another installation must not be revoked by an old event.
        repositoryRepository.findByGithubRepositoryId(repositoryId)
                .filter(repository -> repository.getInstallationId() == null
                        || repository.getInstallationId().equals(installationId))
                .ifPresent(repository -> {
                    repository.setAccessStatus(RepositoryAccessStatus.REVOKED);
                    repositoryRepository.save(repository);
                });
    }

    private void revokeRepositories(Long installationId) {
        repositoryRepository.findAllByInstallationId(installationId).forEach(repository -> {
            repository.setAccessStatus(RepositoryAccessStatus.REVOKED);
            repositoryRepository.save(repository);
        });
    }

    //---- Sanitized payloads keep the id at the root; raw GitHub payloads keep it under "installation".
    private Long readInstallationId(JsonNode root) {
        JsonNode sanitizedId = root.path("installationId");
        if (sanitizedId.isNumber()) {
            return sanitizedId.asLong();
        }
        JsonNode installationId = root.path("installation").path("id");
        return installationId.isNumber() ? installationId.asLong() : null;
    }
}

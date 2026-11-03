package com.devrats.services.activity;

import com.devrats.entities.GitHubEventEntity;
import com.devrats.entities.GitHubInstallationEntity;
import com.devrats.entities.GitHubRepositoryEntity;
import com.devrats.enums.GitHubOwnerType;
import com.devrats.enums.RepositoryAccessStatus;
import com.devrats.repositories.GitHubInstallationRepository;
import com.devrats.repositories.GitHubRepositoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GitHubEventSyncServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");

    @Mock
    private GitHubRepositoryRepository repositoryRepository;
    @Mock
    private GitHubInstallationRepository installationRepository;

    private GitHubEventSyncService service;

    @BeforeEach
    void setUp() {
        service = new GitHubEventSyncService(repositoryRepository, installationRepository,
                new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void shouldApplyAddedAndRemovedRepositories_whenInstallationRepositoriesEvent() {
        GitHubEventEntity event = event("installation_repositories", """
                {"eventType":"installation_repositories","action":"added","installationId":55,
                 "repositories_added":[
                   {"id":101,"name":"api","full_name":"org/api","private":true,"fork":false,"archived":false,
                    "owner":{"id":10,"login":"org","type":"Organization"}}],
                 "repositories_removed":[{"id":102}]}
                """);
        GitHubRepositoryEntity removed = repository(102L, 55L);
        when(repositoryRepository.findByGithubRepositoryId(101L)).thenReturn(Optional.empty());
        when(repositoryRepository.findByGithubRepositoryId(102L)).thenReturn(Optional.of(removed));

        service.handle(event);

        ArgumentCaptor<GitHubRepositoryEntity> captor = ArgumentCaptor.forClass(GitHubRepositoryEntity.class);
        verify(repositoryRepository, times(2)).save(captor.capture());
        GitHubRepositoryEntity added = captor.getAllValues().stream()
                .filter(repository -> repository.getGithubRepositoryId() == 101L)
                .findFirst()
                .orElseThrow();

        assertThat(added.getInstallationId()).isEqualTo(55L);
        assertThat(added.getFullName()).isEqualTo("org/api");
        assertThat(added.getVisibility()).isEqualTo("PRIVATE");
        assertThat(added.getOwnerType()).isEqualTo(GitHubOwnerType.ORGANIZATION);
        assertThat(added.getAccessStatus()).isEqualTo(RepositoryAccessStatus.ACCESSIBLE);
        assertThat(removed.getAccessStatus()).isEqualTo(RepositoryAccessStatus.REVOKED);
    }

    @Test
    void shouldNotRevokeRepository_whenItBelongsToAnotherInstallation() {
        GitHubEventEntity event = event("installation_repositories", """
                {"eventType":"installation_repositories","action":"removed","installationId":55,
                 "repositories_removed":[{"id":102}]}
                """);
        GitHubRepositoryEntity repository = repository(102L, 66L);
        when(repositoryRepository.findByGithubRepositoryId(102L)).thenReturn(Optional.of(repository));

        service.handle(event);

        assertThat(repository.getAccessStatus()).isEqualTo(RepositoryAccessStatus.ACCESSIBLE);
        verify(repositoryRepository, never()).save(any());
    }

    @Test
    void shouldSuspendInstallationAndRevokeRepositories_whenInstallationDeleted() {
        GitHubEventEntity event = event("installation", """
                {"eventType":"installation","action":"deleted","installationId":55}
                """);
        GitHubInstallationEntity first = installation();
        GitHubInstallationEntity second = installation();
        GitHubRepositoryEntity firstRepository = repository(101L, 55L);
        GitHubRepositoryEntity secondRepository = repository(102L, 55L);
        when(installationRepository.findAllByInstallationId(55L)).thenReturn(List.of(first, second));
        when(repositoryRepository.findAllByInstallationId(55L))
                .thenReturn(List.of(firstRepository, secondRepository));

        service.handle(event);

        assertThat(first.getSuspendedAt()).isEqualTo(NOW);
        assertThat(second.getSuspendedAt()).isEqualTo(NOW);
        assertThat(firstRepository.getAccessStatus()).isEqualTo(RepositoryAccessStatus.REVOKED);
        assertThat(secondRepository.getAccessStatus()).isEqualTo(RepositoryAccessStatus.REVOKED);
        verify(installationRepository, times(2)).save(any(GitHubInstallationEntity.class));
    }

    private GitHubEventEntity event(String eventType, String payload) {
        GitHubEventEntity event = new GitHubEventEntity();
        event.setEventType(eventType);
        event.setPayload(payload);
        return event;
    }

    private GitHubInstallationEntity installation() {
        GitHubInstallationEntity installation = new GitHubInstallationEntity();
        installation.setId(UUID.randomUUID());
        installation.setInstallationId(55L);
        installation.setAccountLogin("org");
        installation.setAccountType("Organization");
        return installation;
    }

    private GitHubRepositoryEntity repository(Long githubRepositoryId, Long installationId) {
        GitHubRepositoryEntity repository = new GitHubRepositoryEntity();
        repository.setGithubRepositoryId(githubRepositoryId);
        repository.setInstallationId(installationId);
        repository.setOwnerLogin("org");
        repository.setOwnerType(GitHubOwnerType.ORGANIZATION);
        repository.setRepositoryName("repo");
        repository.setFullName("org/repo");
        repository.setVisibility("PRIVATE");
        repository.setAccessStatus(RepositoryAccessStatus.ACCESSIBLE);
        return repository;
    }
}

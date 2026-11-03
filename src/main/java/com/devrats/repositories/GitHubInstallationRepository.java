package com.devrats.repositories;

import com.devrats.entities.GitHubInstallationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GitHubInstallationRepository extends JpaRepository<GitHubInstallationEntity, UUID> {

    Optional<GitHubInstallationEntity> findByGithubAccountIdAndInstallationId(UUID githubAccountId, Long installationId);

    List<GitHubInstallationEntity> findAllByInstallationId(Long installationId);

    List<GitHubInstallationEntity> findAllByGithubAccountId(UUID githubAccountId);
}

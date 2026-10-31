package com.devrats.repositories;

import com.devrats.entities.GitHubAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GitHubAccountRepository extends JpaRepository<GitHubAccountEntity, UUID> {

    Optional<GitHubAccountEntity> findByUserId(UUID userId);

    Optional<GitHubAccountEntity> findByGithubUserId(Long githubUserId);

    Optional<GitHubAccountEntity> findByGithubUsernameIgnoreCase(String githubUsername);

    boolean existsByGithubUserId(Long githubUserId);

    boolean existsByUserId(UUID userId);
}

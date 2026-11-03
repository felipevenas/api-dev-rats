package com.devrats.repositories;

import com.devrats.entities.GitHubRepositoryEntity;
import com.devrats.enums.RepositoryAccessStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GitHubRepositoryRepository extends JpaRepository<GitHubRepositoryEntity, UUID> {

    Optional<GitHubRepositoryEntity> findByGithubRepositoryId(Long githubRepositoryId);

    List<GitHubRepositoryEntity> findAllByOwnerGithubUserId(Long ownerGithubUserId);

    List<GitHubRepositoryEntity> findAllByGithubRepositoryIdIn(Collection<Long> githubRepositoryIds);

    List<GitHubRepositoryEntity> findAllByAccessStatus(String accessStatus);

    List<GitHubRepositoryEntity> findAllByInstallationId(Long installationId);

    List<GitHubRepositoryEntity> findAllByInstallationIdInOrderByFullNameAsc(Collection<Long> installationIds);

    org.springframework.data.domain.Page<GitHubRepositoryEntity> findAllByInstallationIdInAndFullNameContainingIgnoreCase(
            Collection<Long> installationIds, String query, org.springframework.data.domain.Pageable pageable);

    long countByInstallationIdInAndAccessStatusNot(Collection<Long> installationIds,
                                                   RepositoryAccessStatus accessStatus);
}

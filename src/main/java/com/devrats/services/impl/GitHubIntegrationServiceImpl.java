package com.devrats.services.impl;

import com.devrats.config.AppProperties;
import com.devrats.config.CacheConfig;
import com.devrats.config.GitHubAppProperties;
import com.devrats.dto.GitHubAuthorizeResponse;
import com.devrats.dto.GitHubRepositoryResponse;
import com.devrats.dto.GitHubStatusResponse;
import com.devrats.dto.github.GitHubInstallation;
import com.devrats.dto.github.GitHubInstallationList;
import com.devrats.dto.github.GitHubRepository;
import com.devrats.dto.github.GitHubRepositoryList;
import com.devrats.dto.github.GitHubTokenResponse;
import com.devrats.dto.github.GitHubUser;
import com.devrats.entities.GitHubAccountEntity;
import com.devrats.entities.GitHubInstallationEntity;
import com.devrats.entities.GitHubRepositoryEntity;
import com.devrats.entities.UserEntity;
import com.devrats.enums.AvatarSource;
import com.devrats.enums.GitHubConnectionStatus;
import com.devrats.enums.GitHubOwnerType;
import com.devrats.enums.NotificationType;
import com.devrats.enums.RepositoryAccessStatus;
import com.devrats.exceptions.ConflictException;
import com.devrats.exceptions.DomainException;
import com.devrats.exceptions.ErrorCode;
import com.devrats.exceptions.ForbiddenException;
import com.devrats.exceptions.NotFoundException;
import com.devrats.repositories.GitHubAccountRepository;
import com.devrats.repositories.GitHubInstallationRepository;
import com.devrats.repositories.GitHubRepositoryRepository;
import com.devrats.repositories.UserRepository;
import com.devrats.services.AuditService;
import com.devrats.services.GitHubIntegrationService;
import com.devrats.services.NotificationService;
import com.devrats.services.github.GitHubApiClient;
import com.devrats.services.github.GitHubAppTokenProvider;
import com.devrats.utils.TokenCipherHelper;
import com.devrats.utils.TokenHelper;
import com.devrats.utils.UserViewHelper;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class GitHubIntegrationServiceImpl implements GitHubIntegrationService {

    private final GitHubAccountRepository accountRepository;
    private final GitHubInstallationRepository installationRepository;
    private final GitHubRepositoryRepository repositoryRepository;
    private final UserRepository userRepository;
    private final GitHubApiClient apiClient;
    private final GitHubAppTokenProvider tokenProvider;
    private final GitHubAppProperties githubProperties;
    private final AppProperties appProperties;
    private final TokenCipherHelper cipherHelper;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final CacheManager cacheManager;
    private final Clock clock;

    public GitHubIntegrationServiceImpl(GitHubAccountRepository accountRepository,
                                        GitHubInstallationRepository installationRepository,
                                        GitHubRepositoryRepository repositoryRepository,
                                        UserRepository userRepository,
                                        GitHubApiClient apiClient,
                                        GitHubAppTokenProvider tokenProvider,
                                        GitHubAppProperties githubProperties,
                                        AppProperties appProperties,
                                        TokenCipherHelper cipherHelper,
                                        AuditService auditService,
                                        NotificationService notificationService,
                                        CacheManager cacheManager,
                                        Clock clock) {
        this.accountRepository = accountRepository;
        this.installationRepository = installationRepository;
        this.repositoryRepository = repositoryRepository;
        this.userRepository = userRepository;
        this.apiClient = apiClient;
        this.tokenProvider = tokenProvider;
        this.githubProperties = githubProperties;
        this.appProperties = appProperties;
        this.cipherHelper = cipherHelper;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.cacheManager = cacheManager;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public GitHubStatusResponse getStatus(UUID userId) {
        GitHubAccountEntity account = accountRepository.findByUserId(userId).orElse(null);
        if (account == null || account.getConnectionStatus() != GitHubConnectionStatus.CONNECTED) {
            return new GitHubStatusResponse(githubProperties.isConfigured(), false, null, null, 0, 0,
                    List.of(), null, null,
                    githubProperties.isConfigured() ? "GitHub não conectado" : "GitHub App não configurada");
        }
        List<Long> installationIds = installationRepository.findAllByGithubAccountId(account.getId()).stream()
                .map(GitHubInstallationEntity::getInstallationId)
                .toList();
        int repositoryCount = installationIds.isEmpty() ? 0
                : (int) repositoryRepository.countByInstallationIdIn(installationIds);
        return new GitHubStatusResponse(true, true, account.getGithubUsername(), account.getGithubUserId(),
                installationIds.size(), repositoryCount, List.of("metadata:read", "contents:read"),
                account.getConnectedAt(), account.getLastSyncedAt(), null);
    }

    @Override
    public GitHubAuthorizeResponse getAuthorizeUrl(UUID userId) {
        requireConfigured();
        String state = issueState(userId);
        String url = UriComponentsBuilder.fromUriString(githubProperties.getOauthUrl() + "/authorize")
                .queryParam("client_id", githubProperties.getClientId())
                .queryParam("redirect_uri", githubProperties.getCallbackUrl())
                .queryParam("state", state)
                .build()
                .toUriString();
        return new GitHubAuthorizeResponse(url);
    }

    @Override
    public GitHubAuthorizeResponse getInstallUrl(UUID userId) {
        requireConfigured();
        String state = issueState(userId);
        String slug = githubProperties.getAppSlug() == null || githubProperties.getAppSlug().isBlank()
                ? githubProperties.getAppId() : githubProperties.getAppSlug();
        return new GitHubAuthorizeResponse("https://github.com/apps/" + slug + "/installations/new?state=" + state);
    }

    @Override
    @Transactional
    public String handleCallback(String code, String state) {
        UUID userId = consumeState(state);
        GitHubTokenResponse token = apiClient.exchangeCode(code);
        if (token == null || token.accessToken() == null) {
            throw new DomainException(ErrorCode.GITHUB_NOT_CONNECTED, "GitHub recusou a autorização");
        }
        GitHubUser githubUser = apiClient.getAuthenticatedUser(token.accessToken());
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND));
        applyGitHubAvatar(user, githubUser.avatarUrl());

        accountRepository.findByGithubUserId(githubUser.id())
                .filter(other -> !other.getUser().getId().equals(userId))
                .ifPresent(other -> {
                    throw new ConflictException(ErrorCode.GITHUB_ALREADY_CONNECTED);
                });

        GitHubAccountEntity account = accountRepository.findByUserId(userId).orElseGet(GitHubAccountEntity::new);
        account.setUser(user);
        account.setGithubUserId(githubUser.id());
        account.setGithubUsername(githubUser.login());
        account.setAvatarUrl(githubUser.avatarUrl());
        account.setConnectionStatus(GitHubConnectionStatus.CONNECTED);
        account.setDisconnectedAt(null);
        account.setTokenKeyVersion(cipherHelper.getKeyId());
        storeTokens(account, token);
        accountRepository.save(account);

        syncInstallations(account, token.accessToken());
        auditService.record(userId, "GITHUB_ACCOUNT", account.getId(), com.devrats.enums.AuditAction.GITHUB_CONNECTED, null);
        return appProperties.getFrontendUrl() + "?github=connected";
    }

    @Override
    @Transactional
    public String handleSetup(Long installationId, String setupAction, String state) {
        UUID userId = consumeState(state);
        GitHubAccountEntity account = accountRepository.findByUserId(userId)
                .orElseThrow(() -> new DomainException(ErrorCode.GITHUB_NOT_CONNECTED));
        String userToken = ensureUserToken(account);
        GitHubInstallationList authorized = apiClient.listUserInstallations(userToken);
        GitHubInstallation installationInfo = authorized == null || authorized.installations() == null ? null
                : authorized.installations().stream()
                .filter(item -> installationId.equals(item.id()))
                .findFirst()
                .orElse(null);
        if (installationInfo == null) {
            throw new ForbiddenException("A instalação não pertence à sua conta GitHub");
        }

        GitHubInstallationEntity installation = installationRepository.findByInstallationId(installationId)
                .orElseGet(GitHubInstallationEntity::new);
        if (installation.getId() != null
                && !installation.getGithubAccount().getId().equals(account.getId())) {
            throw new ConflictException(ErrorCode.GITHUB_ALREADY_CONNECTED,
                    "Esta instalação já está vinculada a outra conta");
        }
        installation.setGithubAccount(account);
        installation.setInstallationId(installationId);
        installation.setAccountLogin(installationInfo.account() == null
                ? account.getGithubUsername() : installationInfo.account().login());
        installation.setAccountType(installationInfo.account() == null
                ? "User" : installationInfo.account().type());
        installation.setSuspendedAt(installationInfo.suspendedAt());
        installationRepository.save(installation);
        syncInstallationRepositories(installation.getInstallationId());
        return appProperties.getFrontendUrl() + "?github=installed";
    }

    @Override
    @Transactional
    public void disconnect(UUID userId) {
        GitHubAccountEntity account = accountRepository.findByUserId(userId)
                .orElseThrow(() -> new DomainException(ErrorCode.GITHUB_NOT_CONNECTED));
        Instant now = clock.instant();
        account.setConnectionStatus(GitHubConnectionStatus.REVOKED);
        account.setEncryptedAccessToken(null);
        account.setEncryptedRefreshToken(null);
        account.setDisconnectedAt(now);
        accountRepository.save(account);

        List<Long> installationIds = installationRepository.findAllByGithubAccountId(account.getId()).stream()
                .map(GitHubInstallationEntity::getInstallationId)
                .toList();
        if (!installationIds.isEmpty()) {
            repositoryRepository.findAllByInstallationIdInOrderByFullNameAsc(installationIds).forEach(repository -> {
                repository.setAccessStatus(RepositoryAccessStatus.REVOKED);
                repositoryRepository.save(repository);
            });
        }
        auditService.record(userId, "GITHUB_ACCOUNT", account.getId(),
                com.devrats.enums.AuditAction.GITHUB_DISCONNECTED, null);
        notificationService.notify(userId, NotificationType.INTEGRATION_ATTENTION, null,
                "github-disconnected:" + account.getId(),
                java.util.Map.of("reason", "GitHub desconectado; a verificação de commits está pausada"));
    }

    @Override
    @Transactional
    public void syncRepositories(UUID userId) {
        GitHubAccountEntity account = accountRepository.findByUserId(userId)
                .filter(found -> found.getConnectionStatus() == GitHubConnectionStatus.CONNECTED)
                .orElseThrow(() -> new DomainException(ErrorCode.GITHUB_NOT_CONNECTED));
        List<GitHubInstallationEntity> installations = installationRepository.findAllByGithubAccountId(account.getId());
        if (installations.isEmpty()) {
            String userToken = ensureUserToken(account);
            syncInstallations(account, userToken);
            return;
        }
        installations.forEach(installation -> syncInstallationRepositories(installation.getInstallationId()));
        account.setLastSyncedAt(clock.instant());
        accountRepository.save(account);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<GitHubRepositoryResponse> listRepositories(UUID userId, String query, Pageable pageable) {
        GitHubAccountEntity account = accountRepository.findByUserId(userId).orElse(null);
        if (account == null || account.getConnectionStatus() != GitHubConnectionStatus.CONNECTED) {
            return Page.empty(pageable);
        }
        List<Long> installationIds = installationRepository.findAllByGithubAccountId(account.getId()).stream()
                .map(GitHubInstallationEntity::getInstallationId)
                .toList();
        if (installationIds.isEmpty()) {
            return Page.empty(pageable);
        }
        String filter = query == null ? "" : query.trim();
        return repositoryRepository
                .findAllByInstallationIdInAndFullNameContainingIgnoreCase(installationIds, filter, pageable)
                .map(this::toResponse);
    }

    private void syncInstallations(GitHubAccountEntity account, String userAccessToken) {
        GitHubInstallationList installations = apiClient.listUserInstallations(userAccessToken);
        if (installations == null || installations.installations() == null) {
            return;
        }
        for (GitHubInstallation installation : installations.installations()) {
            GitHubInstallationEntity entity = installationRepository.findByInstallationId(installation.id())
                    .orElseGet(GitHubInstallationEntity::new);
            entity.setGithubAccount(account);
            entity.setInstallationId(installation.id());
            entity.setAccountLogin(installation.account().login());
            entity.setAccountType(installation.account().type());
            entity.setSuspendedAt(installation.suspendedAt());
            installationRepository.save(entity);
            syncInstallationRepositories(installation.id());
        }
        account.setLastSyncedAt(clock.instant());
        accountRepository.save(account);
    }

    private void syncInstallationRepositories(Long installationId) {
        String installationToken = tokenProvider.getInstallationToken(installationId);
        GitHubRepositoryList repositories = apiClient.listInstallationRepositories(installationToken);
        if (repositories == null || repositories.repositories() == null) {
            return;
        }
        for (GitHubRepository repository : repositories.repositories()) {
            GitHubRepositoryEntity entity = repositoryRepository.findByGithubRepositoryId(repository.id())
                    .orElseGet(GitHubRepositoryEntity::new);
            entity.setGithubRepositoryId(repository.id());
            entity.setOwnerGithubUserId(repository.owner().id());
            entity.setOwnerLogin(repository.owner().login());
            entity.setOwnerType("Organization".equalsIgnoreCase(repository.owner().type())
                    ? GitHubOwnerType.ORGANIZATION : GitHubOwnerType.USER);
            entity.setRepositoryName(repository.name());
            entity.setFullName(repository.fullName());
            entity.setVisibility(repository.privateRepository() ? "PRIVATE" : "PUBLIC");
            entity.setFork(repository.fork());
            entity.setArchived(repository.archived());
            entity.setInstallationId(installationId);
            entity.setAccessStatus(RepositoryAccessStatus.ACCESSIBLE);
            entity.setLastSyncedAt(clock.instant());
            repositoryRepository.save(entity);
        }
    }

    private void applyGitHubAvatar(UserEntity user, String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isBlank() || user.getAvatarSource() == AvatarSource.CUSTOM) {
            return;
        }
        user.setAvatarUrl(avatarUrl);
        user.setAvatarSource(AvatarSource.GITHUB);
        userRepository.save(user);
    }

    private void storeTokens(GitHubAccountEntity account, GitHubTokenResponse token) {
        Instant now = clock.instant();
        if (token.accessToken() != null) {
            account.setEncryptedAccessToken(cipherHelper.encrypt(token.accessToken()));
            account.setAccessTokenExpiresAt(token.expiresIn() == null ? null : now.plusSeconds(token.expiresIn()));
        }
        if (token.refreshToken() != null) {
            account.setEncryptedRefreshToken(cipherHelper.encrypt(token.refreshToken()));
            account.setRefreshTokenExpiresAt(token.refreshTokenExpiresIn() == null
                    ? null : now.plusSeconds(token.refreshTokenExpiresIn()));
        }
    }

    private String ensureUserToken(GitHubAccountEntity account) {
        Instant now = clock.instant();
        //---- Apps created with "Expire user authorization tokens" disabled return no expires_in,
        //---- so a stored token without expiry is valid until the user revokes it.
        if (account.getEncryptedAccessToken() != null
                && (account.getAccessTokenExpiresAt() == null
                || account.getAccessTokenExpiresAt().isAfter(now.plusSeconds(60)))) {
            return cipherHelper.decrypt(account.getEncryptedAccessToken());
        }
        String refreshToken = cipherHelper.decrypt(account.getEncryptedRefreshToken());
        if (refreshToken == null || account.getRefreshTokenExpiresAt() == null
                || account.getRefreshTokenExpiresAt().isBefore(now)) {
            throw new DomainException(ErrorCode.GITHUB_NOT_CONNECTED, "Conexão expirada; reconecte o GitHub");
        }
        GitHubTokenResponse refreshed = apiClient.refreshUserToken(refreshToken);
        if (refreshed == null || refreshed.accessToken() == null) {
            throw new DomainException(ErrorCode.GITHUB_NOT_CONNECTED, "Não foi possível renovar a conexão");
        }
        storeTokens(account, refreshed);
        accountRepository.save(account);
        return refreshed.accessToken();
    }

    private String issueState(UUID userId) {
        String state = TokenHelper.randomToken();
        Cache cache = cacheManager.getCache(CacheConfig.OAUTH_STATES);
        if (cache != null) {
            cache.put(state, userId);
        }
        return state;
    }

    private UUID consumeState(String state) {
        Cache cache = cacheManager.getCache(CacheConfig.OAUTH_STATES);
        if (!(cache instanceof CaffeineCache caffeineCache) || state == null) {
            throw new ForbiddenException("Estado OAuth inválido");
        }
        //---- Atomic remove: a state can be consumed exactly once even under concurrent requests.
        Object value = caffeineCache.getNativeCache().asMap().remove(state);
        if (!(value instanceof UUID userId)) {
            throw new ForbiddenException("Estado OAuth inválido ou expirado");
        }
        return userId;
    }

    private GitHubRepositoryResponse toResponse(GitHubRepositoryEntity entity) {
        return new GitHubRepositoryResponse(entity.getId(), entity.getGithubRepositoryId(), entity.getFullName(),
                entity.getOwnerLogin(), entity.getVisibility(), "PRIVATE".equals(entity.getVisibility()),
                entity.isFork(), entity.getAccessStatus().name());
    }

    private void requireConfigured() {
        if (!githubProperties.isConfigured()) {
            throw new DomainException(ErrorCode.GITHUB_NOT_CONNECTED, "GitHub App não configurada");
        }
    }
}

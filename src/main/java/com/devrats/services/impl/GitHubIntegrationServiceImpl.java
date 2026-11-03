package com.devrats.services.impl;

import com.devrats.config.AppProperties;
import com.devrats.config.CacheConfig;
import com.devrats.config.GitHubAppProperties;
import com.devrats.dto.GitHubAuthorizeResponse;
import com.devrats.dto.GitHubRepositoryResponse;
import com.devrats.dto.GitHubStatusResponse;
import com.devrats.dto.github.GitHubInstallation;
import com.devrats.dto.github.GitHubRepository;
import com.devrats.dto.github.GitHubTokenResponse;
import com.devrats.dto.github.GitHubUser;
import com.devrats.entities.GitHubAccountEntity;
import com.devrats.entities.GitHubInstallationEntity;
import com.devrats.entities.GitHubRepositoryEntity;
import com.devrats.entities.UserEntity;
import com.devrats.enums.AuditAction;
import com.devrats.enums.AvatarSource;
import com.devrats.enums.GitHubConnectionStatus;
import com.devrats.enums.GitHubOwnerType;
import com.devrats.enums.NotificationType;
import com.devrats.enums.RepositoryAccessStatus;
import com.devrats.exceptions.ConflictException;
import com.devrats.exceptions.DomainException;
import com.devrats.exceptions.ErrorCode;
import com.devrats.exceptions.ForbiddenException;
import com.devrats.exceptions.GitHubApiException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class GitHubIntegrationServiceImpl implements GitHubIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(GitHubIntegrationServiceImpl.class);

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
    private final TransactionTemplate transactionTemplate;
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
                                        PlatformTransactionManager transactionManager,
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
        this.transactionTemplate = new TransactionTemplate(transactionManager);
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
                : (int) repositoryRepository.countByInstallationIdInAndAccessStatusNot(installationIds,
                RepositoryAccessStatus.REVOKED);
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
    public String handleCallback(String code, String state, String error) {
        if (isPresent(error)) {
            discardState(state);
            return errorRedirect(githubErrorReason(error));
        }
        if (!isPresent(code) || !isPresent(state)) {
            discardState(state);
            return errorRedirect("invalid_request");
        }
        try {
            UUID userId = consumeState(state);
            GitHubTokenResponse token = apiClient.exchangeCode(code);
            if (token == null || !isPresent(token.accessToken())) {
                return errorRedirect("github_denied");
            }
            GitHubUser githubUser = apiClient.getAuthenticatedUser(token.accessToken());
            if (githubUser == null || githubUser.id() == null) {
                return errorRedirect("github_error");
            }

            UUID accountId = persistAccount(userId, githubUser, token);
            try {
                syncInstallations(accountId, token.accessToken());
            } catch (RuntimeException exception) {
                //---- A conta já está conectada; a instalação pode ser sincronizada depois.
                log.warn("Falha ao sincronizar instalações da conta {}: {}", userId, exception.getMessage());
            }
            log.info("Conta GitHub conectada para o usuário {}", userId);
            return successRedirect("connected");
        } catch (ForbiddenException exception) {
            return errorRedirect("invalid_state");
        } catch (ConflictException exception) {
            return errorRedirect("conflict");
        } catch (NotFoundException exception) {
            return errorRedirect("user_not_found");
        } catch (GitHubApiException exception) {
            log.warn("GitHub indisponível no callback: {}", exception.getMessage());
            return errorRedirect("github_unavailable");
        } catch (DomainException exception) {
            return errorRedirect(domainReason(exception));
        } catch (RuntimeException exception) {
            log.error("Erro inesperado no callback do GitHub", exception);
            return errorRedirect("integration_error");
        }
    }

    @Override
    public String handleSetup(String installationIdValue, String setupAction, String state, String error) {
        if (isPresent(error)) {
            discardState(state);
            return errorRedirect(githubErrorReason(error));
        }
        Long installationId = parseInstallationId(installationIdValue);
        if (installationId == null || !isPresent(state)) {
            discardState(state);
            return errorRedirect("invalid_request");
        }
        try {
            UUID userId = consumeState(state);
            GitHubAccountEntity account = findConnectedAccount(userId);
            String userToken = resolveUserToken(account);
            GitHubInstallation installation = apiClient.listUserInstallations(userToken).stream()
                    .filter(item -> installationId.equals(item.id()))
                    .findFirst()
                    .orElse(null);
            if (installation == null) {
                return errorRedirect("installation_not_owned");
            }

            persistInstallation(account.getId(), installation);
            syncInstallationRepositories(installationId);
            touchLastSynced(account.getId());
            log.info("Instalação {} vinculada ao usuário {}", installationId, userId);
            return successRedirect("installed");
        } catch (ForbiddenException exception) {
            return errorRedirect("invalid_state");
        } catch (NotFoundException exception) {
            return errorRedirect("user_not_found");
        } catch (GitHubApiException exception) {
            log.warn("GitHub indisponível no setup: {}", exception.getMessage());
            return errorRedirect("github_unavailable");
        } catch (DomainException exception) {
            return errorRedirect(domainReason(exception));
        } catch (RuntimeException exception) {
            log.error("Erro inesperado no setup do GitHub", exception);
            return errorRedirect("integration_error");
        }
    }

    @Override
    public void disconnect(UUID userId) {
        GitHubAccountEntity account = accountRepository.findByUserId(userId)
                .orElseThrow(() -> new DomainException(ErrorCode.GITHUB_NOT_CONNECTED));
        String accessToken = decryptToken(account.getEncryptedAccessToken());
        if (accessToken != null) {
            //---- Best effort: a GitHub failure must never prevent the local disconnect.
            try {
                apiClient.revokeUserAuthorization(accessToken);
            } catch (RuntimeException exception) {
                log.warn("Falha ao revogar a autorização do GitHub do usuário {}: {}",
                        userId, exception.getMessage());
            }
        }

        UUID accountId = transactionTemplate.execute(status -> markDisconnected(userId));
        if (accountId != null) {
            notificationService.notify(userId, NotificationType.INTEGRATION_ATTENTION, null,
                    "github-disconnected:" + accountId,
                    Map.of("reason", "GitHub desconectado; a verificação de commits está pausada"));
        }
        log.info("Conta GitHub desconectada para o usuário {}", userId);
    }

    @Override
    public void syncRepositories(UUID userId) {
        GitHubAccountEntity account = findConnectedAccount(userId);
        List<Long> installationIds = installationRepository.findAllByGithubAccountId(account.getId()).stream()
                .filter(installation -> installation.getSuspendedAt() == null)
                .map(GitHubInstallationEntity::getInstallationId)
                .toList();
        if (installationIds.isEmpty()) {
            syncInstallations(account.getId(), resolveUserToken(account));
            return;
        }
        installationIds.forEach(this::syncInstallationRepositories);
        touchLastSynced(account.getId());
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

    private UUID persistAccount(UUID userId, GitHubUser githubUser, GitHubTokenResponse token) {
        UUID accountId = transactionTemplate.execute(status -> {
            UserEntity user = userRepository.findById(userId)
                    .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND));
            accountRepository.findByGithubUserId(githubUser.id())
                    .filter(other -> !other.getUser().getId().equals(userId))
                    .ifPresent(other -> {
                        throw new ConflictException(ErrorCode.GITHUB_ALREADY_CONNECTED);
                    });

            GitHubAccountEntity account = accountRepository.findByUserId(userId)
                    .orElseGet(GitHubAccountEntity::new);
            account.setUser(user);
            account.setGithubUserId(githubUser.id());
            account.setGithubUsername(githubUser.login());
            account.setAvatarUrl(githubUser.avatarUrl());
            account.setConnectionStatus(GitHubConnectionStatus.CONNECTED);
            account.setDisconnectedAt(null);
            account.setTokenKeyVersion(cipherHelper.getKeyId());
            storeTokens(account, token);
            GitHubAccountEntity saved = accountRepository.save(account);
            applyGitHubAvatar(user, githubUser.avatarUrl());
            return saved.getId();
        });
        if (accountId == null) {
            throw new DomainException(ErrorCode.INTERNAL_ERROR, "Não foi possível salvar a conta do GitHub");
        }
        auditService.record(userId, "GITHUB_ACCOUNT", accountId, AuditAction.GITHUB_CONNECTED, null);
        return accountId;
    }

    /**
     * Sincroniza as instalações visíveis para a conta. As listagens do GitHub são paginadas no cliente
     * e cada persistência acontece em uma transação curta, sem HTTP dentro do banco.
     */
    private void syncInstallations(UUID accountId, String userAccessToken) {
        List<GitHubInstallation> installations = apiClient.listUserInstallations(userAccessToken);
        if (installations == null) {
            return;
        }
        Set<Long> currentInstallationIds = new HashSet<>();
        for (GitHubInstallation installation : installations) {
            if (installation.id() == null) {
                continue;
            }
            currentInstallationIds.add(installation.id());
            persistInstallation(accountId, installation);
        }
        pruneInstallations(accountId, currentInstallationIds);
        for (Long installationId : currentInstallationIds) {
            try {
                syncInstallationRepositories(installationId);
            } catch (RuntimeException exception) {
                log.warn("Falha ao sincronizar a instalação {}: {}", installationId, exception.getMessage());
            }
        }
        touchLastSynced(accountId);
    }

    private void persistInstallation(UUID accountId, GitHubInstallation installation) {
        transactionTemplate.executeWithoutResult(status -> {
            GitHubAccountEntity account = accountRepository.findById(accountId)
                    .orElseThrow(() -> new DomainException(ErrorCode.GITHUB_NOT_CONNECTED));
            GitHubInstallationEntity entity = installationRepository
                    .findByGithubAccountIdAndInstallationId(accountId, installation.id())
                    .orElseGet(GitHubInstallationEntity::new);
            entity.setGithubAccount(account);
            entity.setInstallationId(installation.id());
            entity.setAccountLogin(installation.account() == null
                    ? account.getGithubUsername() : installation.account().login());
            entity.setAccountType(installation.account() == null
                    ? "User" : installation.account().type());
            entity.setSuspendedAt(installation.suspendedAt());
            installationRepository.save(entity);
        });
    }

    //---- Remove vínculos antigos da conta (ex.: reconexão com outro GitHub) e libera repositórios órfãos.
    private void pruneInstallations(UUID accountId, Set<Long> currentInstallationIds) {
        transactionTemplate.executeWithoutResult(status -> {
            List<GitHubInstallationEntity> existing = installationRepository.findAllByGithubAccountId(accountId);
            for (GitHubInstallationEntity installation : existing) {
                Long installationId = installation.getInstallationId();
                if (currentInstallationIds.contains(installationId)) {
                    continue;
                }
                installationRepository.delete(installation);
                installationRepository.flush();
                if (installationRepository.findAllByInstallationId(installationId).isEmpty()) {
                    revokeRepositories(installationId);
                }
            }
        });
    }

    private void syncInstallationRepositories(Long installationId) {
        String installationToken = tokenProvider.getInstallationToken(installationId);
        List<GitHubRepository> repositories = apiClient.listInstallationRepositories(installationToken);
        if (repositories == null) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> persistRepositories(installationId, repositories));
    }

    private void persistRepositories(Long installationId, List<GitHubRepository> repositories) {
        Set<Long> currentRepositoryIds = new HashSet<>();
        for (GitHubRepository repository : repositories) {
            if (repository.id() == null) {
                continue;
            }
            currentRepositoryIds.add(repository.id());
            GitHubRepositoryEntity entity = repositoryRepository.findByGithubRepositoryId(repository.id())
                    .orElseGet(GitHubRepositoryEntity::new);
            entity.setGithubRepositoryId(repository.id());
            entity.setOwnerGithubUserId(repository.owner() == null ? null : repository.owner().id());
            entity.setOwnerLogin(repository.owner() == null || repository.owner().login() == null
                    ? "unknown" : repository.owner().login());
            entity.setOwnerType(repository.owner() != null
                    && "Organization".equalsIgnoreCase(repository.owner().type())
                    ? GitHubOwnerType.ORGANIZATION : GitHubOwnerType.USER);
            entity.setRepositoryName(repository.name());
            entity.setFullName(repository.fullName());
            entity.setVisibility(repository.visibility() == null
                    ? (repository.privateRepository() ? "PRIVATE" : "PUBLIC") : repository.visibility());
            entity.setFork(repository.fork());
            entity.setArchived(repository.archived());
            entity.setInstallationId(installationId);
            entity.setAccessStatus(RepositoryAccessStatus.ACCESSIBLE);
            entity.setLastSyncedAt(clock.instant());
            repositoryRepository.save(entity);
        }
        repositoryRepository.flush();
        //---- Só os repositórios desta instalação podem ser revogados; outra conta/instalação fica intacta.
        repositoryRepository.findAllByInstallationId(installationId).stream()
                .filter(repository -> !currentRepositoryIds.contains(repository.getGithubRepositoryId()))
                .filter(repository -> repository.getAccessStatus() != RepositoryAccessStatus.REVOKED)
                .forEach(repository -> {
                    repository.setAccessStatus(RepositoryAccessStatus.REVOKED);
                    repositoryRepository.save(repository);
                });
    }

    private UUID markDisconnected(UUID userId) {
        GitHubAccountEntity account = accountRepository.findByUserId(userId)
                .orElseThrow(() -> new DomainException(ErrorCode.GITHUB_NOT_CONNECTED));
        account.setConnectionStatus(GitHubConnectionStatus.REVOKED);
        account.setEncryptedAccessToken(null);
        account.setEncryptedRefreshToken(null);
        account.setDisconnectedAt(clock.instant());
        accountRepository.save(account);

        List<Long> installationIds = installationRepository.findAllByGithubAccountId(account.getId()).stream()
                .map(GitHubInstallationEntity::getInstallationId)
                .toList();
        for (Long installationId : installationIds) {
            boolean sharedByConnectedAccount = installationRepository.findAllByInstallationId(installationId).stream()
                    .filter(installation -> !installation.getGithubAccount().getId().equals(account.getId()))
                    .anyMatch(installation -> installation.getGithubAccount().getConnectionStatus()
                            == GitHubConnectionStatus.CONNECTED);
            if (!sharedByConnectedAccount) {
                revokeRepositories(installationId);
            }
        }
        auditService.record(userId, "GITHUB_ACCOUNT", account.getId(), AuditAction.GITHUB_DISCONNECTED, null);
        return account.getId();
    }

    private void revokeRepositories(Long installationId) {
        repositoryRepository.findAllByInstallationId(installationId).forEach(repository -> {
            if (repository.getAccessStatus() != RepositoryAccessStatus.REVOKED) {
                repository.setAccessStatus(RepositoryAccessStatus.REVOKED);
                repositoryRepository.save(repository);
            }
        });
    }

    private void touchLastSynced(UUID accountId) {
        transactionTemplate.executeWithoutResult(status -> accountRepository.findById(accountId)
                .ifPresent(account -> {
                    account.setLastSyncedAt(clock.instant());
                    accountRepository.save(account);
                }));
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

    /**
     * Resolve o token de usuário, renovando via HTTP fora de qualquer transação de banco.
     */
    private String resolveUserToken(GitHubAccountEntity account) {
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
        transactionTemplate.executeWithoutResult(status -> accountRepository.findById(account.getId())
                .ifPresent(managed -> {
                    storeTokens(managed, refreshed);
                    accountRepository.save(managed);
                }));
        return refreshed.accessToken();
    }

    private GitHubAccountEntity findConnectedAccount(UUID userId) {
        return accountRepository.findByUserId(userId)
                .filter(account -> account.getConnectionStatus() == GitHubConnectionStatus.CONNECTED)
                .orElseThrow(() -> new DomainException(ErrorCode.GITHUB_NOT_CONNECTED,
                        "Conecte o GitHub antes de instalar a App"));
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

    //---- Failed authorizations must not leave a live state behind for a later replay.
    private void discardState(String state) {
        if (!isPresent(state)) {
            return;
        }
        Cache cache = cacheManager.getCache(CacheConfig.OAUTH_STATES);
        if (cache instanceof CaffeineCache caffeineCache) {
            caffeineCache.getNativeCache().asMap().remove(state);
        }
    }

    private GitHubRepositoryResponse toResponse(GitHubRepositoryEntity entity) {
        return new GitHubRepositoryResponse(entity.getId(), entity.getGithubRepositoryId(), entity.getFullName(),
                entity.getOwnerLogin(), entity.getVisibility(), "PRIVATE".equals(entity.getVisibility()),
                entity.isFork(), entity.getAccessStatus().name());
    }

    private String successRedirect(String status) {
        return redirect(status, null);
    }

    private String errorRedirect(String reason) {
        return redirect("error", reason);
    }

    private String redirect(String status, String reason) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(appProperties.getFrontendUrl())
                .queryParam("github", status);
        if (reason != null) {
            builder.queryParam("reason", reason);
        }
        return builder.build().toUriString();
    }

    //---- Nunca ecoa o texto recebido do GitHub: apenas motivos conhecidos e curtos chegam ao frontend.
    private String githubErrorReason(String error) {
        return switch (error) {
            case "access_denied" -> "access_denied";
            case "redirect_uri_mismatch" -> "redirect_uri_mismatch";
            case "application_suspended" -> "application_suspended";
            default -> "github_error";
        };
    }

    private String domainReason(DomainException exception) {
        return switch (exception.getErrorCode()) {
            case GITHUB_NOT_CONNECTED -> "not_connected";
            case GITHUB_UNAVAILABLE -> "github_unavailable";
            default -> "domain_error";
        };
    }

    private Long parseInstallationId(String value) {
        if (!isPresent(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String decryptToken(byte[] encryptedToken) {
        if (encryptedToken == null) {
            return null;
        }
        try {
            return cipherHelper.decrypt(encryptedToken);
        } catch (RuntimeException exception) {
            log.warn("Não foi possível decifrar o token do GitHub: {}", exception.getMessage());
            return null;
        }
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private void requireConfigured() {
        if (!githubProperties.isConfigured()) {
            throw new DomainException(ErrorCode.GITHUB_NOT_CONNECTED, "GitHub App não configurada");
        }
    }
}

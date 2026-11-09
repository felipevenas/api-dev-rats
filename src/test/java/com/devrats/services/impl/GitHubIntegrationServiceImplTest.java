package com.devrats.services.impl;

import com.devrats.config.AppProperties;
import com.devrats.config.CacheConfig;
import com.devrats.config.GitHubAppProperties;
import com.devrats.dto.github.GitHubInstallation;
import com.devrats.dto.github.GitHubTokenResponse;
import com.devrats.dto.github.GitHubUser;
import com.devrats.entities.GitHubAccountEntity;
import com.devrats.entities.GitHubInstallationEntity;
import com.devrats.entities.UserEntity;
import com.devrats.enums.GitHubConnectionStatus;
import com.devrats.exceptions.GitHubApiException;
import com.devrats.repositories.GitHubAccountRepository;
import com.devrats.repositories.GitHubInstallationRepository;
import com.devrats.repositories.GitHubRepositoryRepository;
import com.devrats.repositories.UserRepository;
import com.devrats.services.AuditService;
import com.devrats.services.NotificationService;
import com.devrats.services.github.GitHubApiClient;
import com.devrats.services.github.GitHubAppTokenProvider;
import com.devrats.utils.TokenCipherHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GitHubIntegrationServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");

    @Mock
    private GitHubAccountRepository accountRepository;
    @Mock
    private GitHubInstallationRepository installationRepository;
    @Mock
    private GitHubRepositoryRepository repositoryRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private GitHubApiClient apiClient;
    @Mock
    private GitHubAppTokenProvider tokenProvider;
    @Mock
    private TokenCipherHelper cipherHelper;
    @Mock
    private AuditService auditService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private PlatformTransactionManager transactionManager;

    private GitHubIntegrationServiceImpl service;
    private CacheManager cacheManager;
    private GitHubAppProperties githubProperties;
    private AppProperties appProperties;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(org.mockito.Mockito.mock(TransactionStatus.class));
        githubProperties = new GitHubAppProperties();
        appProperties = new AppProperties();
        appProperties.setFrontendUrl("http://localhost:5173");
        cacheManager = new CacheConfig().cacheManager();
        service = buildService();
    }

    private GitHubIntegrationServiceImpl buildService() {
        return new GitHubIntegrationServiceImpl(accountRepository, installationRepository, repositoryRepository,
                userRepository, apiClient, tokenProvider, githubProperties, appProperties, cipherHelper,
                auditService, notificationService, cacheManager, transactionManager,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void shouldUseSlugReportedByGithub_whenBuildingInstallUrl() {
        configureGithubApp();
        when(tokenProvider.getAppSlug()).thenReturn("devrats-app");

        String url = service.getInstallUrl(UUID.randomUUID()).url();

        assertThat(url).startsWith("https://github.com/apps/devrats-app/installations/new?state=");
    }

    @Test
    void shouldFallBackToConfiguredSlug_whenGithubSlugLookupFails() {
        configureGithubApp();
        when(tokenProvider.getAppSlug()).thenThrow(new GitHubApiException(502, "GitHub respondeu 502"));

        String url = service.getInstallUrl(UUID.randomUUID()).url();

        assertThat(url).startsWith("https://github.com/apps/devrats-app/installations/new?state=");
    }

    private void configureGithubApp() {
        githubProperties.setAppId("5546729");
        githubProperties.setClientId("client");
        githubProperties.setClientSecret("secret");
        githubProperties.setPrivateKey("pem");
        githubProperties.setWebhookSecret("secret");
        githubProperties.setAppSlug("devrats-app");
        githubProperties.setCallbackUrl("http://localhost:8080/api/v1/integrations/github/callback");
        githubProperties.setSetupUrl("http://localhost:8080/api/v1/integrations/github/setup");
        service = buildService();
    }

    @Test
    void shouldRedirectWithSafeReason_whenGithubReturnsAccessDenied() {
        String url = service.handleCallback(null, null, "access_denied");

        assertThat(url).isEqualTo("http://localhost:5173?github=error&reason=access_denied");
    }

    @Test
    void shouldNotEchoGithubErrorText_inCallbackRedirect() {
        String url = service.handleCallback(null, null, "<script>alert(1)</script>");

        assertThat(url).isEqualTo("http://localhost:5173?github=error&reason=github_error");
    }

    @Test
    void shouldDiscardState_whenGithubDeniesAuthorization() {
        UUID userId = UUID.randomUUID();
        cacheManager.getCache(CacheConfig.OAUTH_STATES).put("state", userId);

        service.handleCallback(null, "state", "access_denied");

        assertThat(cacheManager.getCache(CacheConfig.OAUTH_STATES).get("state")).isNull();
    }

    @Test
    void shouldRedirectInvalidRequest_whenSetupHasInvalidInstallationId() {
        String url = service.handleSetup("not-a-number", null, "state", null);

        assertThat(url).isEqualTo("http://localhost:5173?github=error&reason=invalid_request");
    }

    @Test
    void shouldRevokeGithubGrant_andClearTokens_whenDisconnect() {
        UUID userId = UUID.randomUUID();
        GitHubAccountEntity account = connectedAccount(userId);
        byte[] encryptedToken = new byte[]{1, 2, 3};
        account.setEncryptedAccessToken(encryptedToken);
        account.setEncryptedRefreshToken(encryptedToken);
        when(accountRepository.findByUserId(userId)).thenReturn(Optional.of(account));
        when(accountRepository.save(account)).thenReturn(account);
        when(cipherHelper.decrypt(encryptedToken)).thenReturn("gh-token");
        when(installationRepository.findAllByGithubAccountId(account.getId())).thenReturn(List.of());

        service.disconnect(userId);

        verify(apiClient).revokeUserAuthorization("gh-token");
        assertThat(account.getConnectionStatus()).isEqualTo(GitHubConnectionStatus.REVOKED);
        assertThat(account.getEncryptedAccessToken()).isNull();
        assertThat(account.getEncryptedRefreshToken()).isNull();
        assertThat(account.getDisconnectedAt()).isEqualTo(NOW);
        verify(notificationService).notify(eq(userId), any(), any(), any(), any());
    }

    @Test
    void shouldSkipGithubRevocation_whenAccountHasNoToken() {
        UUID userId = UUID.randomUUID();
        GitHubAccountEntity account = connectedAccount(userId);
        when(accountRepository.findByUserId(userId)).thenReturn(Optional.of(account));
        when(accountRepository.save(account)).thenReturn(account);
        when(installationRepository.findAllByGithubAccountId(account.getId())).thenReturn(List.of());

        service.disconnect(userId);

        verify(apiClient, never()).revokeUserAuthorization(any());
    }

    @Test
    void shouldNotRevokeRepositories_whenInstallationIsSharedWithAnotherConnectedAccount() {
        UUID userId = UUID.randomUUID();
        GitHubAccountEntity account = connectedAccount(userId);
        GitHubAccountEntity other = connectedAccount(UUID.randomUUID());
        GitHubInstallationEntity ownInstallation = installation(account, 10L);
        GitHubInstallationEntity otherInstallation = installation(other, 10L);
        when(accountRepository.findByUserId(userId)).thenReturn(Optional.of(account));
        when(accountRepository.save(account)).thenReturn(account);
        when(installationRepository.findAllByGithubAccountId(account.getId())).thenReturn(List.of(ownInstallation));
        when(installationRepository.findAllByInstallationId(10L))
                .thenReturn(List.of(ownInstallation, otherInstallation));

        service.disconnect(userId);

        verify(repositoryRepository, never()).findAllByInstallationId(10L);
    }

    @Test
    void shouldPruneOldInstallationsAndConnectAccount_whenCallbackSucceeds() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        GitHubUser githubUser = new GitHubUser(99L, "octocat", "User", "https://avatar.test/octocat");
        GitHubAccountEntity account = new GitHubAccountEntity();
        GitHubInstallationEntity stale = installation(new GitHubAccountEntity(), 1L);
        cacheManager.getCache(CacheConfig.OAUTH_STATES).put("state", userId);

        when(apiClient.exchangeCode("code"))
                .thenReturn(new GitHubTokenResponse("user-token", null, null, null, "bearer", null));
        when(apiClient.getAuthenticatedUser("user-token")).thenReturn(githubUser);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(accountRepository.findByGithubUserId(99L)).thenReturn(Optional.empty());
        when(accountRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(accountRepository.save(any(GitHubAccountEntity.class))).thenAnswer(invocation -> {
            GitHubAccountEntity saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(accountRepository.findById(any(UUID.class))).thenReturn(Optional.of(account));
        when(apiClient.listUserInstallations("user-token")).thenReturn(List.of(
                new GitHubInstallation(2L, new GitHubUser(10L, "org", "Organization", null), "all", null)));
        when(installationRepository.findByGithubAccountIdAndInstallationId(any(), eq(2L)))
                .thenReturn(Optional.empty());
        when(installationRepository.save(any(GitHubInstallationEntity.class))).thenAnswer(invocation -> {
            GitHubInstallationEntity saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(installationRepository.findAllByGithubAccountId(any(UUID.class))).thenReturn(List.of(stale));
        when(installationRepository.findAllByInstallationId(1L)).thenReturn(List.of());
        when(tokenProvider.getInstallationToken(2L)).thenReturn("installation-token");
        when(apiClient.listInstallationRepositories("installation-token")).thenReturn(List.of());

        String url = service.handleCallback("code", "state", null);

        assertThat(url).isEqualTo("http://localhost:5173?github=connected");
        verify(installationRepository).delete(stale);
        verify(installationRepository).flush();
    }

    private GitHubAccountEntity connectedAccount(UUID userId) {
        GitHubAccountEntity account = new GitHubAccountEntity();
        account.setId(UUID.randomUUID());
        UserEntity user = new UserEntity();
        user.setId(userId);
        account.setUser(user);
        account.setGithubUserId(123L);
        account.setGithubUsername("octocat");
        account.setConnectionStatus(GitHubConnectionStatus.CONNECTED);
        return account;
    }

    private GitHubInstallationEntity installation(GitHubAccountEntity account, Long installationId) {
        GitHubInstallationEntity installation = new GitHubInstallationEntity();
        installation.setId(UUID.randomUUID());
        installation.setGithubAccount(account);
        installation.setInstallationId(installationId);
        installation.setAccountLogin(account.getGithubUsername());
        installation.setAccountType("User");
        return installation;
    }
}

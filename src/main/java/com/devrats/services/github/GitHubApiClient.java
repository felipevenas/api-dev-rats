package com.devrats.services.github;

import com.devrats.config.GitHubAppProperties;
import com.devrats.dto.github.GitHubApp;
import com.devrats.dto.github.GitHubCommit;
import com.devrats.dto.github.GitHubInstallation;
import com.devrats.dto.github.GitHubInstallationList;
import com.devrats.dto.github.GitHubInstallationTokenResponse;
import com.devrats.dto.github.GitHubRepository;
import com.devrats.dto.github.GitHubRepositoryList;
import com.devrats.dto.github.GitHubTokenResponse;
import com.devrats.dto.github.GitHubUser;
import com.devrats.exceptions.GitHubApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class GitHubApiClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubApiClient.class);
    private static final String API_VERSION = "2022-11-28";
    private static final int PAGE_SIZE = 100;
    //---- Safety cap: a compromised or misbehaving API cannot make the sync loop forever.
    private static final int MAX_PAGES = 50;

    private final RestClient restClient;
    private final GitHubAppProperties properties;

    public GitHubApiClient(GitHubAppProperties properties) {
        //---- Boot 4 splits the HTTP client starters; a plain builder keeps this dependency-free.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(20));
        this.restClient = RestClient.builder().requestFactory(factory).build();
        this.properties = properties;
    }

    public GitHubTokenResponse exchangeCode(String code) {
        Map<String, String> body = Map.of(
                "client_id", properties.getClientId(),
                "client_secret", properties.getClientSecret(),
                "code", code);
        return execute(() -> restClient.post()
                .uri(properties.getOauthUrl() + "/access_token")
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(GitHubTokenResponse.class));
    }

    public GitHubTokenResponse refreshUserToken(String refreshToken) {
        Map<String, String> body = Map.of(
                "client_id", properties.getClientId(),
                "client_secret", properties.getClientSecret(),
                "grant_type", "refresh_token",
                "refresh_token", refreshToken);
        return execute(() -> restClient.post()
                .uri(properties.getOauthUrl() + "/access_token")
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(GitHubTokenResponse.class));
    }

    public GitHubUser getAuthenticatedUser(String accessToken) {
        return execute(() -> restClient.get()
                .uri(properties.getApiUrl() + "/user")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .header("X-GitHub-Api-Version", API_VERSION)
                .retrieve()
                .body(GitHubUser.class));
    }

    public List<GitHubInstallation> listUserInstallations(String accessToken) {
        List<GitHubInstallation> installations = new ArrayList<>();
        boolean complete = false;
        for (int page = 1; page <= MAX_PAGES && !complete; page++) {
            GitHubInstallationList response = listUserInstallationsPage(accessToken, page);
            if (response == null || response.installations() == null || response.installations().isEmpty()) {
                complete = true;
            } else {
                installations.addAll(response.installations());
                complete = isComplete(installations.size(), response.totalCount(), response.installations().size());
            }
        }
        if (!complete) {
            log.warn("GitHub installation list truncated after {} pages", MAX_PAGES);
        }
        return installations;
    }

    public List<GitHubRepository> listInstallationRepositories(String installationToken) {
        List<GitHubRepository> repositories = new ArrayList<>();
        boolean complete = false;
        for (int page = 1; page <= MAX_PAGES && !complete; page++) {
            GitHubRepositoryList response = listInstallationRepositoriesPage(installationToken, page);
            if (response == null || response.repositories() == null || response.repositories().isEmpty()) {
                complete = true;
            } else {
                repositories.addAll(response.repositories());
                complete = isComplete(repositories.size(), response.totalCount(), response.repositories().size());
            }
        }
        if (!complete) {
            log.warn("GitHub repository list truncated after {} pages", MAX_PAGES);
        }
        return repositories;
    }

    //---- GitHub Apps revoke the user-to-server grant by basic-authenticating with client id/secret.
    public void revokeUserAuthorization(String accessToken) {
        execute(() -> restClient.method(HttpMethod.DELETE)
                .uri(properties.getApiUrl() + "/applications/" + properties.getClientId() + "/grant")
                .headers(headers -> headers.setBasicAuth(properties.getClientId(), properties.getClientSecret()))
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-GitHub-Api-Version", API_VERSION)
                .body(Map.of("access_token", accessToken))
                .retrieve()
                .toBodilessEntity());
    }

    public GitHubRepository getRepository(String installationToken, String fullName) {
        return execute(() -> restClient.get()
                .uri(properties.getApiUrl() + "/repos/" + fullName)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + installationToken)
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .header("X-GitHub-Api-Version", API_VERSION)
                .retrieve()
                .body(GitHubRepository.class));
    }

    public GitHubCommit getCommit(String installationToken, String fullName, String sha) {
        return execute(() -> restClient.get()
                .uri(properties.getApiUrl() + "/repos/" + fullName + "/commits/" + sha)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + installationToken)
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .header("X-GitHub-Api-Version", API_VERSION)
                .retrieve()
                .body(GitHubCommit.class));
    }

    public List<GitHubCommit> listCommits(String installationToken, String fullName, Instant since, int page, int perPage) {
        String uri = properties.getApiUrl() + "/repos/" + fullName + "/commits?since=" + since
                + "&page=" + page + "&per_page=" + perPage;
        GitHubCommit[] commits = execute(() -> restClient.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + installationToken)
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .header("X-GitHub-Api-Version", API_VERSION)
                .retrieve()
                .body(GitHubCommit[].class));
        return commits == null ? List.of() : List.of(commits);
    }

    public GitHubApp getApp(String appJwt) {
        return execute(() -> restClient.get()
                .uri(properties.getApiUrl() + "/app")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + appJwt)
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .header("X-GitHub-Api-Version", API_VERSION)
                .retrieve()
                .body(GitHubApp.class));
    }

    public GitHubInstallationTokenResponse createInstallationToken(String appJwt, Long installationId) {
        return execute(() -> restClient.post()
                .uri(properties.getApiUrl() + "/app/installations/" + installationId + "/access_tokens")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + appJwt)
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .header("X-GitHub-Api-Version", API_VERSION)
                .retrieve()
                .body(GitHubInstallationTokenResponse.class));
    }

    private GitHubInstallationList listUserInstallationsPage(String accessToken, int page) {
        return execute(() -> restClient.get()
                .uri(properties.getApiUrl() + "/user/installations?per_page=" + PAGE_SIZE + "&page=" + page)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .header("X-GitHub-Api-Version", API_VERSION)
                .retrieve()
                .body(GitHubInstallationList.class));
    }

    private GitHubRepositoryList listInstallationRepositoriesPage(String installationToken, int page) {
        return execute(() -> restClient.get()
                .uri(properties.getApiUrl() + "/installation/repositories?per_page=" + PAGE_SIZE + "&page=" + page)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + installationToken)
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .header("X-GitHub-Api-Version", API_VERSION)
                .retrieve()
                .body(GitHubRepositoryList.class));
    }

    private boolean isComplete(int loaded, int totalCount, int pageSize) {
        //---- total_count is authoritative; some GitHub endpoints cap a page below per_page.
        return totalCount > 0 ? loaded >= totalCount : pageSize < PAGE_SIZE;
    }

    private <T> T execute(GitHubRequest<T> request) {
        try {
            return request.execute();
        } catch (RestClientResponseException exception) {
            throw new GitHubApiException(exception.getStatusCode().value(),
                    "GitHub respondeu " + exception.getStatusCode().value());
        }
    }

    @FunctionalInterface
    private interface GitHubRequest<T> {
        T execute();
    }
}

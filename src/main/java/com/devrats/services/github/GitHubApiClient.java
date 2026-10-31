package com.devrats.services.github;

import com.devrats.config.GitHubAppProperties;
import com.devrats.dto.github.GitHubCommit;
import com.devrats.dto.github.GitHubInstallationList;
import com.devrats.dto.github.GitHubInstallationTokenResponse;
import com.devrats.dto.github.GitHubRepository;
import com.devrats.dto.github.GitHubRepositoryList;
import com.devrats.dto.github.GitHubTokenResponse;
import com.devrats.dto.github.GitHubUser;
import com.devrats.exceptions.GitHubApiException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class GitHubApiClient {

    private static final String API_VERSION = "2022-11-28";

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

    public GitHubInstallationList listUserInstallations(String accessToken) {
        return execute(() -> restClient.get()
                .uri(properties.getApiUrl() + "/user/installations?per_page=100")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .header("X-GitHub-Api-Version", API_VERSION)
                .retrieve()
                .body(GitHubInstallationList.class));
    }

    public GitHubRepositoryList listInstallationRepositories(String installationToken) {
        return execute(() -> restClient.get()
                .uri(properties.getApiUrl() + "/installation/repositories?per_page=100")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + installationToken)
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .header("X-GitHub-Api-Version", API_VERSION)
                .retrieve()
                .body(GitHubRepositoryList.class));
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

    public GitHubInstallationTokenResponse createInstallationToken(String appJwt, Long installationId) {
        return execute(() -> restClient.post()
                .uri(properties.getApiUrl() + "/app/installations/" + installationId + "/access_tokens")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + appJwt)
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .header("X-GitHub-Api-Version", API_VERSION)
                .retrieve()
                .body(GitHubInstallationTokenResponse.class));
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

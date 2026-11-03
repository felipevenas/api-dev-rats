package com.devrats.services.github;

import com.devrats.config.GitHubAppProperties;
import com.devrats.dto.github.GitHubInstallation;
import com.devrats.dto.github.GitHubRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

class GitHubApiClientTest {

    private WireMockServer server;
    private GitHubApiClient client;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(options().dynamicPort());
        server.start();

        GitHubAppProperties properties = new GitHubAppProperties();
        properties.setApiUrl("http://localhost:" + server.port());
        properties.setClientId("client-id");
        properties.setClientSecret("client-secret");
        client = new GitHubApiClient(properties);
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void shouldLoadEveryInstallationPage_whenGithubPaginates() {
        server.stubFor(get(urlPathEqualTo("/user/installations"))
                .withQueryParam("page", equalTo("1"))
                .willReturn(okJson("""
                        {"total_count": 2, "installations": [
                          {"id": 1, "account": {"id": 10, "login": "org", "type": "Organization"}}
                        ]}
                        """)));
        server.stubFor(get(urlPathEqualTo("/user/installations"))
                .withQueryParam("page", equalTo("2"))
                .willReturn(okJson("""
                        {"total_count": 2, "installations": [
                          {"id": 2, "account": {"id": 20, "login": "user", "type": "User"}}
                        ]}
                        """)));

        List<GitHubInstallation> installations = client.listUserInstallations("user-token");

        assertThat(installations).extracting(GitHubInstallation::id).containsExactly(1L, 2L);
        server.verify(2, getRequestedFor(urlPathEqualTo("/user/installations")));
    }

    @Test
    void shouldLoadEveryRepositoryPage_whenGithubPaginates() {
        server.stubFor(get(urlPathEqualTo("/installation/repositories"))
                .withQueryParam("page", equalTo("1"))
                .willReturn(okJson("""
                        {"total_count": 2, "repositories": [
                          {"id": 101, "name": "api", "full_name": "org/api", "private": true,
                           "fork": false, "archived": false, "visibility": "private",
                           "owner": {"id": 10, "login": "org", "type": "Organization"}}
                        ]}
                        """)));
        server.stubFor(get(urlPathEqualTo("/installation/repositories"))
                .withQueryParam("page", equalTo("2"))
                .willReturn(okJson("""
                        {"total_count": 2, "repositories": [
                          {"id": 102, "name": "web", "full_name": "org/web", "private": false,
                           "fork": false, "archived": false, "visibility": "public",
                           "owner": {"id": 10, "login": "org", "type": "Organization"}}
                        ]}
                        """)));

        List<GitHubRepository> repositories = client.listInstallationRepositories("installation-token");

        assertThat(repositories).extracting(GitHubRepository::id).containsExactly(101L, 102L);
        server.verify(2, getRequestedFor(urlPathEqualTo("/installation/repositories")));
    }

    @Test
    void shouldRevokeUserGrant_whenDisconnectRequestsIt() {
        server.stubFor(delete(urlEqualTo("/applications/client-id/grant"))
                .willReturn(aResponse().withStatus(204)));

        client.revokeUserAuthorization("gh-token");

        String basicAuth = "Basic " + Base64.getEncoder()
                .encodeToString("client-id:client-secret".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        server.verify(deleteRequestedFor(urlEqualTo("/applications/client-id/grant"))
                .withHeader("Authorization", equalTo(basicAuth))
                .withRequestBody(equalToJson("{\"access_token\":\"gh-token\"}")));
    }
}

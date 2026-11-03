package com.devrats.services.github;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubPayloadParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GitHubPayloadParser parser = new GitHubPayloadParser(objectMapper);

    @Test
    void shouldKeepInstallationRepositoriesLists_whenSanitizingPayload() {
        String payload = """
                {"action":"added","installation":{"id":55},
                 "repositories_added":[
                   {"id":101,"name":"api","full_name":"org/api","private":true,"fork":false,"archived":false,
                    "owner":{"id":10,"login":"org","type":"Organization"}}],
                 "repositories_removed":[
                   {"id":102,"name":"old","full_name":"org/old","private":false,"fork":true,"archived":true,
                    "owner":{"id":10,"login":"org","type":"Organization"}}]}
                """;

        JsonNode sanitized = objectMapper.readTree(parser.sanitize(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "installation_repositories"));

        assertThat(sanitized.path("installationId").asLong()).isEqualTo(55L);
        assertThat(sanitized.path("repositories_added")).hasSize(1);
        assertThat(sanitized.path("repositories_added").get(0).path("full_name").asString()).isEqualTo("org/api");
        assertThat(sanitized.path("repositories_added").get(0).path("owner").path("type").asString())
                .isEqualTo("Organization");
        assertThat(sanitized.path("repositories_removed")).hasSize(1);
        assertThat(sanitized.path("repositories_removed").get(0).path("id").asLong()).isEqualTo(102L);
    }
}

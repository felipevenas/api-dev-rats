package com.devrats.services.github;

import com.devrats.dto.github.GitHubPushPayload;
import com.devrats.dto.github.StoredPushPayload;
import com.devrats.exceptions.DomainException;
import com.devrats.exceptions.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Component
public class GitHubPayloadParser {

    private static final Logger log = LoggerFactory.getLogger(GitHubPayloadParser.class);

    private final ObjectMapper objectMapper;

    public GitHubPayloadParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public GitHubPushPayload parsePush(byte[] payload) {
        try {
            return objectMapper.readValue(payload, GitHubPushPayload.class);
        } catch (Exception e) {
            throw new DomainException(ErrorCode.GITHUB_INVALID_PAYLOAD, "Payload de push inválido");
        }
    }

    public StoredPushPayload parseStored(String payload) {
        try {
            return objectMapper.readValue(payload, StoredPushPayload.class);
        } catch (Exception e) {
            log.debug("Stored GitHub payload is invalid: {}", e.getMessage());
            throw new DomainException(ErrorCode.GITHUB_INVALID_PAYLOAD, "Payload armazenado inválido");
        }
    }

    public Long readInstallationId(byte[] payload) {
        JsonNode node = readTree(payload).path("installation").path("id");
        return node.isNumber() ? node.asLong() : null;
    }

    public Long readRepositoryId(byte[] payload) {
        JsonNode node = readTree(payload).path("repository").path("id");
        return node.isNumber() ? node.asLong() : null;
    }

    //---- Persist only what is needed to reprocess the event: no diffs, file names or commit messages.
    public String sanitize(byte[] payload, String eventType) {
        JsonNode root = readTree(payload);
        ObjectNode sanitized = objectMapper.createObjectNode();
        sanitized.put("eventType", eventType);
        JsonNode action = root.path("action");
        if (action.isString()) {
            sanitized.put("action", action.asString());
        }
        JsonNode installation = root.path("installation");
        if (installation.isObject() && installation.path("id").isNumber()) {
            sanitized.put("installationId", installation.path("id").asLong());
        }
        JsonNode repository = root.path("repository");
        if (!repository.isMissingNode()) {
            sanitized.set("repository", repositorySummary(repository));
        }
        //---- installation_repositories carries the repositories that entered or left the installation.
        sanitizeRepositoryList(root.path("repositories_added"), sanitized, "repositories_added");
        sanitizeRepositoryList(root.path("repositories_removed"), sanitized, "repositories_removed");
        JsonNode commits = root.path("commits");
        if (commits.isArray()) {
            ArrayNode minimalCommits = objectMapper.createArrayNode();
            commits.forEach(commit -> {
                ObjectNode minimal = objectMapper.createObjectNode();
                minimal.set("id", commit.path("id"));
                minimal.set("timestamp", commit.path("timestamp"));
                //---- Keep only the login: names and e-mails are PII and are not needed for validation.
                minimal.set("author", loginOnly(commit.path("author")));
                minimal.set("committer", loginOnly(commit.path("committer")));
                minimal.set("distinct", commit.path("distinct"));
                minimal.put("added", commit.path("added").size());
                minimal.put("removed", commit.path("removed").size());
                minimal.put("modified", commit.path("modified").size());
                minimal.put("parentCount", commit.path("parents").size());
                minimalCommits.add(minimal);
            });
            sanitized.set("commits", minimalCommits);
        }
        return sanitized.toString();
    }

    private ObjectNode loginOnly(JsonNode author) {
        ObjectNode minimal = objectMapper.createObjectNode();
        minimal.set("username", author.path("username"));
        return minimal;
    }

    private void sanitizeRepositoryList(JsonNode repositories, ObjectNode sanitized, String field) {
        if (!repositories.isArray()) {
            return;
        }
        ArrayNode minimalRepositories = objectMapper.createArrayNode();
        repositories.forEach(repository -> minimalRepositories.add(repositorySummary(repository)));
        sanitized.set(field, minimalRepositories);
    }

    private ObjectNode repositorySummary(JsonNode repository) {
        ObjectNode minimal = objectMapper.createObjectNode();
        minimal.set("id", repository.path("id"));
        minimal.set("name", repository.path("name"));
        minimal.set("full_name", repository.path("full_name"));
        minimal.set("private", repository.path("private"));
        minimal.set("fork", repository.path("fork"));
        minimal.set("archived", repository.path("archived"));
        JsonNode owner = repository.path("owner");
        if (owner.isObject()) {
            ObjectNode minimalOwner = objectMapper.createObjectNode();
            minimalOwner.set("id", owner.path("id"));
            minimalOwner.set("login", owner.path("login"));
            minimalOwner.set("type", owner.path("type"));
            minimal.set("owner", minimalOwner);
        }
        return minimal;
    }

    private JsonNode readTree(byte[] payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (Exception e) {
            throw new DomainException(ErrorCode.GITHUB_INVALID_PAYLOAD, "Payload do webhook inválido");
        }
    }
}

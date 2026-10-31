package com.devrats.dto.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubInstallation(
        Long id,
        GitHubUser account,
        @JsonProperty("repository_selection") String repositorySelection,
        @JsonProperty("suspended_at") Instant suspendedAt
) {
}

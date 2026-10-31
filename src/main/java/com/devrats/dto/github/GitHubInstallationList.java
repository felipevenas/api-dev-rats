package com.devrats.dto.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubInstallationList(
        @JsonProperty("total_count") int totalCount,
        List<GitHubInstallation> installations
) {
}

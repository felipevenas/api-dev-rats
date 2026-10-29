package com.devstreak.dto.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubPushPayload(
        String ref,
        GitHubRepository repository,
        GitHubInstallation installation,
        List<GitHubCommit> commits,
        GitHubUser sender
) {
}

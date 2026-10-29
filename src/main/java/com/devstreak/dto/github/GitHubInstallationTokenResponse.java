package com.devstreak.dto.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubInstallationTokenResponse(
        String token,
        @JsonProperty("expires_at") Instant expiresAt
) {
}

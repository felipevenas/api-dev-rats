package com.devrats.dto.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubUser(
        Long id,
        String login,
        String type,
        @JsonProperty("avatar_url") String avatarUrl
) {
}

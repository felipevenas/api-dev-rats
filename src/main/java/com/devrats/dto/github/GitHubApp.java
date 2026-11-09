package com.devrats.dto.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubApp(
        Long id,
        String slug,
        String name
) {
}

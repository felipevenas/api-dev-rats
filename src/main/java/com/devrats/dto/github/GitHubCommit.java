package com.devrats.dto.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubCommit(
        String sha,
        Commit commit,
        GitHubUser author,
        GitHubUser committer,
        List<Parent> parents,
        Stats stats
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Commit(Author author, Author committer, String message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Author(String name, String email, Instant date) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Parent(String sha) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Stats(Integer additions, Integer deletions, Integer total) {
    }
}

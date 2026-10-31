package com.devrats.dto.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StoredPushPayload(
        String eventType,
        StoredRepository repository,
        List<StoredCommit> commits
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StoredRepository(
            Long id,
            @JsonProperty("full_name") String fullName,
            @JsonProperty("private") Boolean privateRepository,
            Boolean fork
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StoredCommit(
            String id,
            String timestamp,
            StoredAuthor author,
            StoredAuthor committer,
            Boolean distinct,
            Integer added,
            Integer removed,
            Integer modified,
            Integer parentCount
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StoredAuthor(String name, String email, String username) {
    }
}

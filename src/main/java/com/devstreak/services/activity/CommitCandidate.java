package com.devstreak.services.activity;

import com.devstreak.enums.CommitObservationSource;

import java.time.Instant;

public record CommitCandidate(
        String sha,
        Long githubAuthorId,
        Long githubCommitterId,
        String authorLogin,
        Instant committedAt,
        Instant firstObservedAt,
        boolean automated,
        boolean empty,
        boolean merge,
        CommitObservationSource source
) {
}

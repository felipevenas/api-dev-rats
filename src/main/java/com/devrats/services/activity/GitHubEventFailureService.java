package com.devrats.services.activity;

import com.devrats.entities.GitHubEventEntity;
import com.devrats.enums.GitHubEventStatus;
import com.devrats.repositories.GitHubEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class GitHubEventFailureService {

    private final GitHubEventRepository eventRepository;
    private final Clock clock;

    public GitHubEventFailureService(GitHubEventRepository eventRepository, Clock clock) {
        this.eventRepository = eventRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID eventId, Exception exception) {
        eventRepository.findById(eventId).ifPresent(event -> {
            int attempts = event.getAttempts() + 1;
            event.setAttempts(attempts);
            event.setStatus(GitHubEventStatus.FAILED);
            event.setErrorCode(exception.getClass().getSimpleName());
            event.setLastError(truncate(exception.getMessage()));
            event.setNextAttemptAt(clock.instant().plus(backoff(attempts)));
            eventRepository.save(event);
        });
    }

    private Duration backoff(int attempts) {
        long seconds = Math.min(3_600, 30L * (1L << Math.min(attempts, 7)));
        return Duration.ofSeconds(seconds);
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}

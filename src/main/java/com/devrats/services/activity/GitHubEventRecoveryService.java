package com.devrats.services.activity;

import com.devrats.enums.GitHubEventStatus;
import com.devrats.repositories.GitHubEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;

@Service
public class GitHubEventRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(GitHubEventRecoveryService.class);
    private static final Duration STUCK_AFTER = Duration.ofMinutes(30);

    private final GitHubEventRepository eventRepository;
    private final Clock clock;

    public GitHubEventRecoveryService(GitHubEventRepository eventRepository, Clock clock) {
        this.eventRepository = eventRepository;
        this.clock = clock;
    }

    //---- An instance that dies mid-processing would otherwise leave events stuck in PROCESSING forever.
    @Scheduled(fixedDelay = 300_000, initialDelay = 120_000)
    @Transactional
    public void resetStuckEvents() {
        int reset = eventRepository.resetStuckProcessing(GitHubEventStatus.PENDING, GitHubEventStatus.PROCESSING,
                clock.instant().minus(STUCK_AFTER));
        if (reset > 0) {
            log.warn("Reset {} stuck GitHub events to PENDING", reset);
        }
    }
}

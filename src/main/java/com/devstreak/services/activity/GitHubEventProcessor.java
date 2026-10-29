package com.devstreak.services.activity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class GitHubEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(GitHubEventProcessor.class);

    private final GitHubEventClaimService claimService;
    private final WebhookProcessingService processingService;
    private final GitHubEventFailureService failureService;

    public GitHubEventProcessor(GitHubEventClaimService claimService,
                                WebhookProcessingService processingService,
                                GitHubEventFailureService failureService) {
        this.claimService = claimService;
        this.processingService = processingService;
        this.failureService = failureService;
    }

    @Scheduled(fixedDelay = 5_000)
    public void processPending() {
        List<UUID> eventIds = claimService.claim(10);
        for (UUID eventId : eventIds) {
            try {
                processingService.processEvent(eventId);
            } catch (Exception exception) {
                log.warn("Webhook event {} failed: {}", eventId, exception.getMessage());
                failureService.markFailed(eventId, exception);
            }
        }
    }
}

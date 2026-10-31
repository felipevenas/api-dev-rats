package com.devrats.services.activity;

import com.devrats.entities.GitHubEventEntity;
import com.devrats.enums.GitHubEventStatus;
import com.devrats.repositories.GitHubEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class WebhookProcessingService {

    private final GitHubEventRepository eventRepository;
    private final ActivityProcessingService activityProcessingService;
    private final GitHubEventSyncService syncService;
    private final Clock clock;

    public WebhookProcessingService(GitHubEventRepository eventRepository,
                                    ActivityProcessingService activityProcessingService,
                                    GitHubEventSyncService syncService,
                                    Clock clock) {
        this.eventRepository = eventRepository;
        this.activityProcessingService = activityProcessingService;
        this.syncService = syncService;
        this.clock = clock;
    }

    @Transactional
    public void processEvent(UUID eventId) {
        GitHubEventEntity event = eventRepository.findById(eventId).orElse(null);
        if (event == null || event.getStatus() == GitHubEventStatus.PROCESSED
                || event.getStatus() == GitHubEventStatus.IGNORED) {
            return;
        }
        if ("push".equals(event.getEventType())) {
            activityProcessingService.processPush(event.getPayload());
        } else {
            syncService.handle(event);
        }
        event.setStatus(GitHubEventStatus.PROCESSED);
        event.setProcessedAt(clock.instant());
        event.setErrorCode(null);
        event.setLastError(null);
        event.setNextAttemptAt(null);
        eventRepository.save(event);
    }
}

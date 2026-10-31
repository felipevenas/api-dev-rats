package com.devrats.services.impl;

import com.devrats.config.AppProperties;
import com.devrats.entities.GitHubEventEntity;
import com.devrats.enums.GitHubEventStatus;
import com.devrats.exceptions.DomainException;
import com.devrats.exceptions.ErrorCode;
import com.devrats.repositories.GitHubEventRepository;
import com.devrats.services.WebhookIngestionService;
import com.devrats.services.github.GitHubPayloadParser;
import com.devrats.services.github.GitHubWebhookVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Set;

@Service
public class WebhookIngestionServiceImpl implements WebhookIngestionService {

    private static final Logger log = LoggerFactory.getLogger(WebhookIngestionServiceImpl.class);
    private static final Set<String> SUPPORTED_EVENTS = Set.of(
            "push", "installation", "installation_repositories", "repository",
            "installation_suspend", "installation_unsuspend");

    private final GitHubWebhookVerifier verifier;
    private final GitHubPayloadParser payloadParser;
    private final GitHubEventRepository eventRepository;
    private final AppProperties properties;
    private final Clock clock;

    public WebhookIngestionServiceImpl(GitHubWebhookVerifier verifier,
                                       GitHubPayloadParser payloadParser,
                                       GitHubEventRepository eventRepository,
                                       AppProperties properties,
                                       Clock clock) {
        this.verifier = verifier;
        this.payloadParser = payloadParser;
        this.eventRepository = eventRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void ingest(String deliveryId, String eventType, byte[] payload, String signature) {
        if (deliveryId == null || deliveryId.isBlank() || eventType == null || eventType.isBlank()) {
            throw new DomainException(ErrorCode.GITHUB_INVALID_PAYLOAD, "Cabeçalhos do webhook ausentes");
        }
        if (payload.length > properties.getWebhook().getMaxPayloadBytes()) {
            throw new DomainException(ErrorCode.PAYLOAD_TOO_LARGE);
        }
        verifier.verify(payload, signature);
        if (eventRepository.existsByDeliveryId(deliveryId)) {
            //---- GitHub retries reuse X-GitHub-Delivery; this is a no-op.
            return;
        }

        GitHubEventEntity event = new GitHubEventEntity();
        event.setDeliveryId(deliveryId);
        event.setEventType(eventType);
        event.setInstallationId(payloadParser.readInstallationId(payload));
        event.setGithubRepositoryId(payloadParser.readRepositoryId(payload));
        event.setPayload(payloadParser.sanitize(payload, eventType));
        if (SUPPORTED_EVENTS.contains(eventType)) {
            event.setStatus(GitHubEventStatus.PENDING);
        } else {
            event.setStatus(GitHubEventStatus.IGNORED);
            event.setProcessedAt(clock.instant());
        }
        try {
            eventRepository.saveAndFlush(event);
        } catch (DataIntegrityViolationException exception) {
            //---- Two deliveries raced on the unique delivery_id; the other one already stored it.
            log.debug("Duplicate webhook delivery ignored: {}", deliveryId);
        }
    }
}

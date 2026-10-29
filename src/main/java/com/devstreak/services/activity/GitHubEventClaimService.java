package com.devstreak.services.activity;

import com.devstreak.entities.GitHubEventEntity;
import com.devstreak.enums.GitHubEventStatus;
import com.devstreak.repositories.GitHubEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class GitHubEventClaimService {

    private final GitHubEventRepository eventRepository;

    public GitHubEventClaimService(GitHubEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Transactional
    public List<UUID> claim(int limit) {
        List<GitHubEventEntity> events = eventRepository.findPendingForUpdate(limit);
        events.forEach(event -> event.setStatus(GitHubEventStatus.PROCESSING));
        eventRepository.saveAll(events);
        return events.stream().map(GitHubEventEntity::getId).toList();
    }
}

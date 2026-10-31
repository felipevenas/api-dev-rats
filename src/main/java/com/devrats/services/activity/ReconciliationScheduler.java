package com.devrats.services.activity;

import com.devrats.enums.ChallengeStatus;
import com.devrats.repositories.ChallengeRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReconciliationScheduler {

    private final ChallengeRepository challengeRepository;
    private final ReconciliationRunner runner;

    public ReconciliationScheduler(ChallengeRepository challengeRepository, ReconciliationRunner runner) {
        this.challengeRepository = challengeRepository;
        this.runner = runner;
    }

    @Scheduled(fixedDelay = 600_000, initialDelay = 60_000)
    public void reconcileActiveChallenges() {
        challengeRepository.findAllByStatus(ChallengeStatus.ACTIVE)
                .forEach(challenge -> runner.reconcile(challenge.getId()));
    }
}

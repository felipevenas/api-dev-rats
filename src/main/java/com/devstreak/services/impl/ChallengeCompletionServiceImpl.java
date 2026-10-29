package com.devstreak.services.impl;

import com.devstreak.entities.ChallengeEntity;
import com.devstreak.entities.ChallengeParticipantEntity;
import com.devstreak.entities.ChallengeResultEntity;
import com.devstreak.entities.MemberDailyActivityEntity;
import com.devstreak.enums.ActivityStatus;
import com.devstreak.enums.AuditAction;
import com.devstreak.enums.ChallengeStatus;
import com.devstreak.enums.NotificationType;
import com.devstreak.enums.ParticipantStatus;
import com.devstreak.enums.ResultStatus;
import com.devstreak.repositories.ChallengeParticipantRepository;
import com.devstreak.repositories.ChallengeRepository;
import com.devstreak.repositories.ChallengeResultRepository;
import com.devstreak.repositories.MemberDailyActivityRepository;
import com.devstreak.services.AuditService;
import com.devstreak.services.ChallengeCompletionService;
import com.devstreak.services.NotificationService;
import com.devstreak.utils.ActivityDateHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ChallengeCompletionServiceImpl implements ChallengeCompletionService {

    private final ChallengeRepository challengeRepository;
    private final ChallengeParticipantRepository participantRepository;
    private final ChallengeResultRepository resultRepository;
    private final MemberDailyActivityRepository activityRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final Clock clock;

    public ChallengeCompletionServiceImpl(ChallengeRepository challengeRepository,
                                          ChallengeParticipantRepository participantRepository,
                                          ChallengeResultRepository resultRepository,
                                          MemberDailyActivityRepository activityRepository,
                                          AuditService auditService,
                                          NotificationService notificationService,
                                          Clock clock) {
        this.challengeRepository = challengeRepository;
        this.participantRepository = participantRepository;
        this.resultRepository = resultRepository;
        this.activityRepository = activityRepository;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void checkAndComplete(UUID challengeId) {
        //---- Row lock serializes concurrent completion checks for the same challenge.
        ChallengeEntity challenge = challengeRepository.findByIdForUpdate(challengeId)
                .orElse(null);
        if (challenge == null || challenge.getStatus() != ChallengeStatus.ACTIVE
                || resultRepository.existsByChallengeId(challengeId)) {
            return;
        }

        List<WinnerCandidate> candidates = participantRepository
                .findAllByChallengeIdOrderByValidDaysDescCurrentStreakDescJoinedAtAsc(challengeId)
                .stream()
                .filter(participant -> participant.getStatus() == ParticipantStatus.ACTIVE)
                .filter(participant -> participant.getValidDays() >= challenge.getTargetValidDays())
                .map(participant -> toCandidate(challenge, participant))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(WinnerCandidate::achievedAt)
                        .thenComparing(candidate -> candidate.participant().getJoinedAt())
                        .thenComparing(candidate -> candidate.participant().getId()))
                .toList();
        if (candidates.isEmpty()) {
            return;
        }

        WinnerCandidate winner = candidates.get(0);
        Instant now = clock.instant();
        ChallengeResultEntity result = new ChallengeResultEntity();
        result.setChallenge(challenge);
        result.setWinnerUser(winner.participant().getUser());
        result.setWinnerParticipant(winner.participant());
        result.setAchievedAt(winner.achievedAt());
        result.setWinningActivityDate(winner.winningDate());
        result.setValidDaysAtWin(winner.participant().getValidDays());
        result.setResultStatus(ResultStatus.FINAL);
        resultRepository.save(result);

        challenge.setStatus(ChallengeStatus.COMPLETED);
        challenge.setCompletedAt(now);
        challengeRepository.save(challenge);

        winner.participant().setCompletedAt(now);
        participantRepository.save(winner.participant());
        auditService.record(winner.participant().getUser().getId(), "CHALLENGE", challengeId,
                AuditAction.CHALLENGE_COMPLETED,
                Map.of("winnerUserId", winner.participant().getUser().getId().toString(),
                        "winningActivityDate", winner.winningDate().toString()));
        participantRepository.findAllByChallengeIdOrderByValidDaysDescCurrentStreakDescJoinedAtAsc(challengeId).stream()
                .filter(participant -> participant.getStatus() == ParticipantStatus.ACTIVE)
                .forEach(participant -> notificationService.notify(participant.getUser().getId(),
                        NotificationType.CHALLENGE_COMPLETED, challengeId,
                        "challenge-completed:" + challengeId + ":" + participant.getUser().getId(),
                        Map.of("winnerUserId", winner.participant().getUser().getId().toString())));
    }

    private WinnerCandidate toCandidate(ChallengeEntity challenge, ChallengeParticipantEntity participant) {
        List<LocalDate> dates = activityRepository.findValidDates(challenge.getId(), participant.getId());
        if (dates.size() < challenge.getTargetValidDays()) {
            return null;
        }
        LocalDate winningDate = dates.get(challenge.getTargetValidDays() - 1);
        Instant achievedAt = activityRepository.findFirstEligibleCommitAt(challenge.getId(), participant.getId(), winningDate)
                .orElseGet(() -> {
                    ZoneId zone = ActivityDateHelper.parseZone(challenge.getTimezone());
                    return winningDate.atStartOfDay(zone).toInstant();
                });
        return new WinnerCandidate(participant, winningDate, achievedAt);
    }

    private record WinnerCandidate(ChallengeParticipantEntity participant, LocalDate winningDate, Instant achievedAt) {
    }
}

package com.devrats.services.impl;

import com.devrats.dto.ActivityCalendarResponse;
import com.devrats.dto.ActivityDayResponse;
import com.devrats.dto.ChallengeProgressResponse;
import com.devrats.dto.ChallengeResultResponse;
import com.devrats.dto.DashboardResponse;
import com.devrats.dto.FeedItemResponse;
import com.devrats.entities.AuditLogEntity;
import com.devrats.entities.ChallengeEntity;
import com.devrats.entities.ChallengeParticipantEntity;
import com.devrats.entities.ChallengeResultEntity;
import com.devrats.entities.GroupEntity;
import com.devrats.enums.ActivityStatus;
import com.devrats.enums.AuditAction;
import com.devrats.enums.ChallengeStatus;
import com.devrats.enums.ParticipantStatus;
import com.devrats.exceptions.ErrorCode;
import com.devrats.exceptions.NotFoundException;
import com.devrats.repositories.AuditLogRepository;
import com.devrats.repositories.ChallengeParticipantRepository;
import com.devrats.repositories.ChallengeRepository;
import com.devrats.repositories.ChallengeResultRepository;
import com.devrats.repositories.GroupRepository;
import com.devrats.repositories.MemberDailyActivityRepository;
import com.devrats.services.GitHubIntegrationService;
import com.devrats.services.GroupAccessService;
import com.devrats.services.ProgressService;
import com.devrats.utils.ActivityDateHelper;
import com.devrats.utils.ChallengeViewHelper;
import com.devrats.utils.StreakHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProgressServiceImpl implements ProgressService {

    private final ChallengeRepository challengeRepository;
    private final ChallengeParticipantRepository participantRepository;
    private final ChallengeResultRepository resultRepository;
    private final MemberDailyActivityRepository activityRepository;
    private final GroupRepository groupRepository;
    private final AuditLogRepository auditLogRepository;
    private final GroupAccessService accessService;
    private final GitHubIntegrationService githubIntegrationService;
    private final Clock clock;

    public ProgressServiceImpl(ChallengeRepository challengeRepository,
                               ChallengeParticipantRepository participantRepository,
                               ChallengeResultRepository resultRepository,
                               MemberDailyActivityRepository activityRepository,
                               GroupRepository groupRepository,
                               AuditLogRepository auditLogRepository,
                               GroupAccessService accessService,
                               GitHubIntegrationService githubIntegrationService,
                               Clock clock) {
        this.challengeRepository = challengeRepository;
        this.participantRepository = participantRepository;
        this.resultRepository = resultRepository;
        this.activityRepository = activityRepository;
        this.groupRepository = groupRepository;
        this.auditLogRepository = auditLogRepository;
        this.accessService = accessService;
        this.githubIntegrationService = githubIntegrationService;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChallengeProgressResponse> listProgress(UUID challengeId, UUID userId) {
        ChallengeEntity challenge = accessService.requireChallengeMember(challengeId, userId);
        return rankedParticipants(challenge);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChallengeProgressResponse> listLeaderboard(UUID challengeId, UUID userId) {
        return listProgress(challengeId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public ActivityCalendarResponse getActivity(UUID challengeId, UUID userId, UUID targetUserId,
                                                LocalDate from, LocalDate to) {
        ChallengeEntity challenge = accessService.requireChallengeMember(challengeId, userId);
        UUID target = targetUserId == null ? userId : targetUserId;
        ChallengeParticipantEntity participant = participantRepository
                .findByChallengeIdAndUserId(challengeId, target)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CHALLENGE_NOT_FOUND));
        ZoneId zone = ActivityDateHelper.parseZone(challenge.getTimezone());
        LocalDate startDate = ActivityDateHelper.toActivityDate(challenge.getStartsAt(), zone);
        LocalDate endDate = challenge.getEndsAt() == null ? null : ActivityDateHelper.toActivityDate(challenge.getEndsAt(), zone);
        LocalDate today = LocalDate.ofInstant(clock.instant(), zone);
        LocalDate rangeFrom = from == null ? startDate : from;
        LocalDate rangeTo = to == null ? today : to;
        if (rangeTo.isBefore(rangeFrom)) {
            throw new com.devrats.exceptions.ValidationException("Intervalo de calendário inválido");
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(rangeFrom, rangeTo) > 366) {
            throw new com.devrats.exceptions.ValidationException("O calendário aceita no máximo 366 dias por consulta");
        }

        Set<LocalDate> validDates = activityRepository
                .findAllByChallengeIdAndParticipantIdAndValidationStatusOrderByActivityDateAsc(
                        challengeId, participant.getId(), ActivityStatus.VALID)
                .stream()
                .map(item -> item.getActivityDate())
                .collect(Collectors.toSet());

        List<ActivityDayResponse> days = new ArrayList<>();
        LocalDate cursor = rangeFrom;
        while (!cursor.isAfter(rangeTo)) {
            String status;
            if (cursor.isBefore(startDate)) {
                status = "BEFORE_START";
            } else if (endDate != null && cursor.isAfter(endDate)) {
                status = "AFTER_END";
            } else if (cursor.isAfter(today)) {
                status = "FUTURE";
            } else if (validDates.contains(cursor)) {
                status = "VALID";
            } else {
                status = "EMPTY";
            }
            days.add(new ActivityDayResponse(cursor, status));
            cursor = cursor.plusDays(1);
        }
        return new ActivityCalendarResponse(challengeId, target, challenge.getTimezone(), rangeFrom, rangeTo, days);
    }

    @Override
    @Transactional(readOnly = true)
    public ChallengeResultResponse getResult(UUID challengeId, UUID userId) {
        accessService.requireChallengeMember(challengeId, userId);
        ChallengeResultEntity result = resultRepository.findByChallengeId(challengeId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.RESULT_NOT_FOUND));
        return new ChallengeResultResponse(challengeId, result.getWinnerUser().getId(),
                result.getWinnerUser().getDisplayName(), result.getWinnerUser().getUsername(), result.getAchievedAt(),
                result.getWinningActivityDate(), result.getValidDaysAtWin(), result.getResultStatus().name(),
                result.getFinalizedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(UUID userId) {
        List<DashboardResponse.DashboardGroup> groups = groupRepository.findAllByMemberUserId(userId).stream()
                .map(group -> toDashboardGroup(group, userId))
                .filter(java.util.Objects::nonNull)
                .toList();
        return new DashboardResponse(groups, githubIntegrationService.getStatus(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<FeedItemResponse> getFeed(UUID challengeId, UUID userId, int limit) {
        ChallengeEntity challenge = accessService.requireChallengeMember(challengeId, userId);
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        List<AuditLogEntity> logs = new ArrayList<>(
                auditLogRepository.findAllByEntityTypeAndEntityIdOrderByCreatedAtDesc("CHALLENGE", challengeId));
        logs.addAll(auditLogRepository.findAllByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                "GROUP", challenge.getGroup().getId()));
        return logs.stream()
                .sorted(java.util.Comparator.comparing(AuditLogEntity::getCreatedAt).reversed())
                .limit(safeLimit)
                .map(this::toFeedItem)
                .toList();
    }

    @Override
    @Transactional
    public void recalculateParticipant(UUID participantId) {
        ChallengeParticipantEntity participant = participantRepository.findByIdForUpdate(participantId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CHALLENGE_NOT_FOUND));
        ChallengeEntity challenge = participant.getChallenge();
        List<LocalDate> dates = activityRepository.findValidDates(challenge.getId(), participant.getId());
        ZoneId zone = ActivityDateHelper.parseZone(challenge.getTimezone());
        LocalDate reference = LocalDate.ofInstant(clock.instant(), zone);
        //---- After the challenge ends, streaks are measured against its last day, not against today.
        if (challenge.getCompletedAt() != null) {
            reference = ActivityDateHelper.toActivityDate(challenge.getCompletedAt(), zone);
        } else if (challenge.getEndsAt() != null) {
            LocalDate endDate = ActivityDateHelper.toActivityDate(challenge.getEndsAt(), zone);
            if (reference.isAfter(endDate)) {
                reference = endDate;
            }
        }
        StreakHelper.StreakResult streak = StreakHelper.calculate(dates, reference);
        participant.setValidDays(dates.size());
        participant.setCurrentStreak(streak.current());
        participant.setBestStreak(Math.max(participant.getBestStreak(), streak.best()));
        participant.setLastValidDate(dates.isEmpty() ? null : dates.get(dates.size() - 1));
        participantRepository.save(participant);
    }

    private List<ChallengeProgressResponse> rankedParticipants(ChallengeEntity challenge) {
        List<ChallengeParticipantEntity> participants = participantRepository
                .findAllByChallengeIdOrderByValidDaysDescCurrentStreakDescJoinedAtAsc(challenge.getId());
        List<ChallengeProgressResponse> response = new ArrayList<>();
        int rank = 1;
        for (ChallengeParticipantEntity participant : participants) {
            if (participant.getStatus() != ParticipantStatus.ACTIVE) {
                continue;
            }
            response.add(ChallengeViewHelper.toProgress(participant, challenge.getTargetValidDays(), rank++));
        }
        return response;
    }

    private DashboardResponse.DashboardGroup toDashboardGroup(GroupEntity group, UUID userId) {
        ChallengeEntity challenge = challengeRepository
                .findFirstByGroupIdAndStatusInOrderBySequenceNoDesc(group.getId(),
                        List.of(ChallengeStatus.DRAFT, ChallengeStatus.ACTIVE))
                .orElseGet(() -> challengeRepository.findAllByGroupIdOrderBySequenceNoDesc(group.getId()).stream()
                        .findFirst().orElse(null));
        if (challenge == null) {
            return null;
        }
        ChallengeParticipantEntity participant = participantRepository
                .findByChallengeIdAndUserId(challenge.getId(), userId)
                .orElse(null);
        if (participant == null) {
            return null;
        }
        List<ChallengeParticipantEntity> ordered = participantRepository
                .findAllByChallengeIdOrderByValidDaysDescCurrentStreakDescJoinedAtAsc(challenge.getId());
        int rank = 1;
        for (ChallengeParticipantEntity item : ordered) {
            if (item.getId().equals(participant.getId())) {
                break;
            }
            if (item.getStatus() == ParticipantStatus.ACTIVE) {
                rank++;
            }
        }
        int percent = Math.min(100, (participant.getValidDays() * 100) / challenge.getTargetValidDays());
        return new DashboardResponse.DashboardGroup(group.getId(), group.getName(), challenge.getId(),
                challenge.getStatus().name(), challenge.getTargetValidDays(), participant.getValidDays(), percent,
                participant.getCurrentStreak(), participant.getBestStreak(), rank, participant.getLastValidDate(),
                challenge.getStartsAt(), challenge.getEndsAt());
    }

    private FeedItemResponse toFeedItem(AuditLogEntity log) {
        return new FeedItemResponse(log.getId(), log.getAction().name(), messageFor(log.getAction()), log.getCreatedAt());
    }

    private String messageFor(AuditAction action) {
        return switch (action) {
            case CHALLENGE_CREATED -> "Desafio criado";
            case CHALLENGE_STARTED -> "Desafio iniciado";
            case CHALLENGE_COMPLETED -> "Alguém alcançou a meta";
            case CHALLENGE_CANCELLED -> "Desafio cancelado";
            case CHALLENGE_EXPIRED -> "Desafio encerrado sem vencedor";
            case MEMBER_JOINED, INVITE_ACCEPTED -> "Novo membro entrou no grupo";
            case DAILY_GOAL -> "Dia válido registrado";
            case ACTIVITY_CORRECTED -> "Atividade corrigida";
            default -> action.name();
        };
    }
}

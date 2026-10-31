package com.devrats.services.impl;

import com.devrats.config.AppProperties;
import com.devrats.dto.AddRepositoryRequest;
import com.devrats.dto.ChallengeResponse;
import com.devrats.dto.CreateChallengeRequest;
import com.devrats.dto.UpdateChallengeRequest;
import com.devrats.entities.ChallengeEntity;
import com.devrats.entities.ChallengeParticipantEntity;
import com.devrats.entities.ChallengeRepositoryEntity;
import com.devrats.entities.GitHubAccountEntity;
import com.devrats.entities.GitHubRepositoryEntity;
import com.devrats.entities.GroupEntity;
import com.devrats.entities.GroupMemberEntity;
import com.devrats.enums.AuditAction;
import com.devrats.enums.ChallengeStatus;
import com.devrats.enums.NotificationType;
import com.devrats.enums.ParticipantStatus;
import com.devrats.enums.RepositoryAccessStatus;
import com.devrats.enums.VerificationMode;
import com.devrats.exceptions.ConflictException;
import com.devrats.exceptions.DomainException;
import com.devrats.exceptions.ErrorCode;
import com.devrats.exceptions.NotFoundException;
import com.devrats.repositories.ChallengeParticipantRepository;
import com.devrats.repositories.ChallengeRepository;
import com.devrats.repositories.ChallengeRepositoryRepository;
import com.devrats.repositories.GitHubAccountRepository;
import com.devrats.repositories.GitHubInstallationRepository;
import com.devrats.repositories.GitHubRepositoryRepository;
import com.devrats.repositories.GroupMemberRepository;
import com.devrats.services.AuditService;
import com.devrats.services.ChallengeService;
import com.devrats.services.GroupAccessService;
import com.devrats.services.NotificationService;
import com.devrats.utils.ActivityDateHelper;
import com.devrats.utils.ChallengeViewHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ChallengeServiceImpl implements ChallengeService {

    private static final Logger log = LoggerFactory.getLogger(ChallengeServiceImpl.class);
    private static final Duration START_TOLERANCE = Duration.ofMinutes(5);
    private static final String TIEBREAK_RULE = "EARLIEST_COMPLETING_COMMIT_THEN_JOINED_AT_THEN_ID";

    private final ChallengeRepository challengeRepository;
    private final ChallengeRepositoryRepository challengeRepositoryRepository;
    private final ChallengeParticipantRepository participantRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GitHubRepositoryRepository githubRepositoryRepository;
    private final GitHubAccountRepository githubAccountRepository;
    private final GitHubInstallationRepository installationRepository;
    private final GroupAccessService accessService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final AppProperties properties;
    private final Clock clock;

    public ChallengeServiceImpl(ChallengeRepository challengeRepository,
                                ChallengeRepositoryRepository challengeRepositoryRepository,
                                ChallengeParticipantRepository participantRepository,
                                GroupMemberRepository groupMemberRepository,
                                GitHubRepositoryRepository githubRepositoryRepository,
                                GitHubAccountRepository githubAccountRepository,
                                GitHubInstallationRepository installationRepository,
                                GroupAccessService accessService,
                                AuditService auditService,
                                NotificationService notificationService,
                                AppProperties properties,
                                Clock clock) {
        this.challengeRepository = challengeRepository;
        this.challengeRepositoryRepository = challengeRepositoryRepository;
        this.participantRepository = participantRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.githubRepositoryRepository = githubRepositoryRepository;
        this.githubAccountRepository = githubAccountRepository;
        this.installationRepository = installationRepository;
        this.accessService = accessService;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ChallengeResponse createDraft(UUID groupId, UUID actorId, CreateChallengeRequest request) {
        GroupMemberEntity owner = accessService.requireOwner(groupId, actorId);
        GroupEntity group = owner.getGroup();
        if (challengeRepository.existsByGroupIdAndStatusIn(groupId, List.of(ChallengeStatus.DRAFT, ChallengeStatus.ACTIVE))) {
            throw new ConflictException(ErrorCode.CHALLENGE_ALREADY_OPEN);
        }
        validateConfiguration(request.targetValidDays(), group.getTimezone(), request.startsAt(), request.endsAt(),
                request.admissionEndsAt(), request.verificationMode(), request.repositoryIds(), actorId);

        ChallengeEntity challenge = new ChallengeEntity();
        challenge.setGroup(group);
        challenge.setSequenceNo(challengeRepository.findMaxSequenceNo(groupId) + 1);
        challenge.setVerificationMode(request.verificationMode());
        challenge.setTargetValidDays(request.targetValidDays());
        //---- The challenge keeps a fixed snapshot of the group timezone.
        challenge.setTimezone(group.getTimezone());
        challenge.setStatus(ChallengeStatus.DRAFT);
        challenge.setStartsAt(request.startsAt());
        challenge.setEndsAt(request.endsAt());
        challenge.setAdmissionEndsAt(request.admissionEndsAt() == null ? request.startsAt() : request.admissionEndsAt());
        challenge.setAllowForks(false);
        challenge.setTiebreakRule(TIEBREAK_RULE);
        challenge.setCreatedBy(owner.getUser());
        challengeRepository.save(challenge);

        if (request.verificationMode() == VerificationMode.SELECTED_REPOSITORIES && request.repositoryIds() != null) {
            addRepositories(challenge, request.repositoryIds(), actorId);
        }
        createMissingParticipants(challenge);
        auditService.record(actorId, "CHALLENGE", challenge.getId(), AuditAction.CHALLENGE_CREATED,
                Map.of("groupId", groupId.toString(), "mode", challenge.getVerificationMode().name()));
        return toResponse(challenge);
    }

    @Override
    @Transactional(readOnly = true)
    public ChallengeResponse getChallenge(UUID challengeId, UUID userId) {
        ChallengeEntity challenge = accessService.requireChallengeMember(challengeId, userId);
        return toResponse(challenge);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChallengeResponse> listChallenges(UUID groupId, UUID userId) {
        accessService.requireMember(groupId, userId);
        return challengeRepository.findAllByGroupIdOrderBySequenceNoDesc(groupId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ChallengeResponse updateChallenge(UUID challengeId, UUID actorId, UpdateChallengeRequest request) {
        ChallengeEntity challenge = accessService.requireChallengeOwner(challengeId, actorId);
        requireDraft(challenge);
        VerificationMode mode = request.verificationMode() == null ? challenge.getVerificationMode() : request.verificationMode();
        int target = request.targetValidDays() == null ? challenge.getTargetValidDays() : request.targetValidDays();
        if (request.timezone() != null && !request.timezone().equals(challenge.getTimezone())) {
            throw new com.devrats.exceptions.ValidationException("O fuso do desafio é fixo após a criação");
        }
        String timezone = challenge.getTimezone();
        Instant startsAt = request.startsAt() == null ? challenge.getStartsAt() : request.startsAt();
        Instant endsAt = request.endsAt() == null ? challenge.getEndsAt() : request.endsAt();
        Instant admissionEndsAt = request.admissionEndsAt() == null ? challenge.getAdmissionEndsAt() : request.admissionEndsAt();
        List<UUID> repositoryIds = request.repositoryIds();
        List<UUID> repositoryIdsForValidation = repositoryIds;
        if (repositoryIdsForValidation == null && mode == VerificationMode.SELECTED_REPOSITORIES) {
            repositoryIdsForValidation = challengeRepositoryRepository.findAllByChallengeIdOrderBySlotAsc(challengeId)
                    .stream()
                    .map(link -> link.getGithubRepository().getId())
                    .toList();
        }
        validateConfiguration(target, timezone, startsAt, endsAt, admissionEndsAt, mode, repositoryIdsForValidation, actorId);

        challenge.setVerificationMode(mode);
        challenge.setTargetValidDays(target);
        challenge.setTimezone(timezone);
        challenge.setStartsAt(startsAt);
        challenge.setEndsAt(endsAt);
        challenge.setAdmissionEndsAt(admissionEndsAt == null ? startsAt : admissionEndsAt);
        challengeRepository.save(challenge);

        if (mode == VerificationMode.OWN_REPOSITORIES) {
            //---- Switching to own repositories drops the old selected list so it cannot be reused later.
            challengeRepositoryRepository.deleteAll(
                    challengeRepositoryRepository.findAllByChallengeIdOrderBySlotAsc(challengeId));
        } else if (repositoryIds != null) {
            List<ChallengeRepositoryEntity> current = challengeRepositoryRepository.findAllByChallengeIdOrderBySlotAsc(challengeId);
            challengeRepositoryRepository.deleteAll(current);
            addRepositories(challenge, repositoryIds, actorId);
        }
        auditService.record(actorId, "CHALLENGE", challengeId, AuditAction.CHALLENGE_UPDATED, null);
        return toResponse(challenge);
    }

    @Override
    @Transactional
    public ChallengeResponse addRepository(UUID challengeId, UUID actorId, AddRepositoryRequest request) {
        accessService.requireChallengeOwner(challengeId, actorId);
        ChallengeEntity challenge = challengeRepository.findByIdForUpdate(challengeId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CHALLENGE_NOT_FOUND));
        requireDraft(challenge);
        if (challenge.getVerificationMode() != VerificationMode.SELECTED_REPOSITORIES) {
            throw new ConflictException(ErrorCode.INVALID_CHALLENGE_STATE,
                    "Este desafio usa repositórios próprios e não aceita repositórios selecionados");
        }
        long count = challengeRepositoryRepository.countByChallengeId(challengeId);
        if (count >= properties.getChallenges().getMaxRepositories()) {
            throw new ConflictException(ErrorCode.REPOSITORY_LIMIT_REACHED);
        }
        GitHubRepositoryEntity repository = requireAccessibleRepository(actorId, request.githubRepositoryId());
        if (challengeRepositoryRepository.existsByChallengeIdAndGithubRepositoryId(challengeId, repository.getId())) {
            throw new ConflictException(ErrorCode.REPOSITORY_ALREADY_LINKED);
        }
        ChallengeRepositoryEntity link = new ChallengeRepositoryEntity();
        link.setChallenge(challenge);
        link.setGithubRepository(repository);
        link.setSlot(nextSlot(challengeId));
        challengeRepositoryRepository.save(link);
        auditService.record(actorId, "CHALLENGE", challengeId, AuditAction.REPOSITORY_ADDED,
                Map.of("repository", repository.getFullName()));
        return toResponse(challenge);
    }

    @Override
    @Transactional
    public void removeRepository(UUID challengeId, UUID actorId, UUID challengeRepositoryId) {
        ChallengeEntity challenge = accessService.requireChallengeOwner(challengeId, actorId);
        requireDraft(challenge);
        ChallengeRepositoryEntity link = challengeRepositoryRepository.findAllByChallengeIdOrderBySlotAsc(challengeId)
                .stream()
                .filter(item -> item.getId().equals(challengeRepositoryId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException(ErrorCode.REPOSITORY_NOT_FOUND));
        if (challengeRepositoryRepository.countByChallengeId(challengeId) <= 1) {
            throw new ConflictException(ErrorCode.INVALID_CHALLENGE_STATE,
                    "Mantenha ao menos um repositório ou altere a modalidade");
        }
        challengeRepositoryRepository.delete(link);
        auditService.record(actorId, "CHALLENGE", challengeId, AuditAction.REPOSITORY_REMOVED, null);
    }

    @Override
    @Transactional
    public ChallengeResponse startChallenge(UUID challengeId, UUID actorId) {
        ChallengeEntity challenge = accessService.requireChallengeOwner(challengeId, actorId);
        requireDraft(challenge);
        start(challenge, actorId);
        return toResponse(challengeRepository.findById(challengeId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CHALLENGE_NOT_FOUND)));
    }

    @Override
    @Transactional
    public ChallengeResponse cancelChallenge(UUID challengeId, UUID actorId) {
        accessService.requireChallengeOwner(challengeId, actorId);
        ChallengeEntity challenge = challengeRepository.findByIdForUpdate(challengeId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.CHALLENGE_NOT_FOUND));
        if (challenge.getStatus() != ChallengeStatus.DRAFT && challenge.getStatus() != ChallengeStatus.ACTIVE) {
            throw new ConflictException(ErrorCode.INVALID_CHALLENGE_STATE);
        }
        challenge.setStatus(ChallengeStatus.CANCELLED);
        challenge.setCancelledAt(clock.instant());
        challengeRepository.save(challenge);
        auditService.record(actorId, "CHALLENGE", challengeId, AuditAction.CHALLENGE_CANCELLED, null);
        notifyParticipants(challenge, NotificationType.CHALLENGE_CANCELLED, "challenge-cancelled:" + challengeId);
        return toResponse(challenge);
    }

    @Override
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void activateScheduledChallenges() {
        Instant now = clock.instant();
        for (ChallengeEntity challenge : challengeRepository.findAllByStatusAndStartsAtLessThanEqual(ChallengeStatus.DRAFT, now)) {
            try {
                start(challenge, challenge.getCreatedBy().getId());
                log.info("Scheduled challenge {} activated", challenge.getId());
            } catch (RuntimeException exception) {
                log.warn("Could not activate challenge {}: {}", challenge.getId(), exception.getMessage());
            }
        }
    }

    @Override
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void expireChallenges() {
        Instant now = clock.instant();
        for (ChallengeEntity challenge : challengeRepository.findAllByStatusAndEndsAtLessThanEqual(ChallengeStatus.ACTIVE, now)) {
            challenge.setStatus(ChallengeStatus.EXPIRED);
            challengeRepository.save(challenge);
            auditService.record(null, "CHALLENGE", challenge.getId(), AuditAction.CHALLENGE_EXPIRED, null);
            notifyParticipants(challenge, NotificationType.CHALLENGE_EXPIRED,
                    "challenge-expired:" + challenge.getId());
        }
    }

    private void start(ChallengeEntity challenge, UUID actorId) {
        challenge = challengeRepository.findByIdForUpdate(challenge.getId())
                .orElseThrow(() -> new NotFoundException(ErrorCode.CHALLENGE_NOT_FOUND));
        if (challenge.getStatus() != ChallengeStatus.DRAFT) {
            throw new ConflictException(ErrorCode.INVALID_CHALLENGE_STATE);
        }
        if (challenge.getVerificationMode() == VerificationMode.SELECTED_REPOSITORIES
                && challengeRepositoryRepository.countByChallengeId(challenge.getId()) == 0) {
            throw new ConflictException(ErrorCode.INVALID_CHALLENGE_STATE,
                    "Selecione pelo menos um repositório antes de iniciar");
        }
        Instant now = clock.instant();
        //---- Starting by hand before the scheduled time moves the window to now.
        if (challenge.getStartsAt().isAfter(now)) {
            challenge.setStartsAt(now);
        }
        challenge.setStatus(ChallengeStatus.ACTIVE);
        challenge.setStartedAt(now);
        //---- Keep the creator's admission window; it may intentionally extend past the start.
        challengeRepository.save(challenge);
        createMissingParticipants(challenge);
        auditService.record(actorId, "CHALLENGE", challenge.getId(), AuditAction.CHALLENGE_STARTED, null);
    }

    private void validateConfiguration(int target, String timezone, Instant startsAt, Instant endsAt,
                                       Instant admissionEndsAt, VerificationMode mode, List<UUID> repositoryIds,
                                       UUID actorId) {
        ActivityDateHelper.parseZone(timezone);
        if (target < properties.getChallenges().getMinTargetDays()
                || target > properties.getChallenges().getMaxTargetDays()) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Meta de dias inválida");
        }
        if (startsAt == null) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Data de início é obrigatória");
        }
        if (startsAt.isBefore(clock.instant().minus(START_TOLERANCE))) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "A data de início não pode estar no passado");
        }
        if (endsAt != null && !endsAt.isAfter(startsAt)) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "O encerramento deve ser após o início");
        }
        if (admissionEndsAt != null && admissionEndsAt.isBefore(startsAt)) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "A admissão não pode terminar antes do início");
        }
        if (mode == VerificationMode.SELECTED_REPOSITORIES) {
            if (repositoryIds == null || repositoryIds.isEmpty()) {
                throw new DomainException(ErrorCode.VALIDATION_ERROR, "Selecione ao menos um repositório");
            }
            if (repositoryIds.size() > properties.getChallenges().getMaxRepositories()) {
                throw new ConflictException(ErrorCode.REPOSITORY_LIMIT_REACHED);
            }
            Set<UUID> unique = new HashSet<>(repositoryIds);
            if (unique.size() != repositoryIds.size()) {
                throw new ConflictException(ErrorCode.REPOSITORY_ALREADY_LINKED);
            }
            repositoryIds.forEach(id -> requireAccessibleRepository(actorId, id));
        } else if (repositoryIds != null && !repositoryIds.isEmpty()) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR,
                    "O modo de repositórios próprios não aceita repositórios selecionados");
        }
    }

    private void addRepositories(ChallengeEntity challenge, List<UUID> repositoryIds, UUID actorId) {
        int slot = 1;
        for (UUID repositoryId : repositoryIds) {
            GitHubRepositoryEntity repository = requireAccessibleRepository(actorId, repositoryId);
            ChallengeRepositoryEntity link = new ChallengeRepositoryEntity();
            link.setChallenge(challenge);
            link.setGithubRepository(repository);
            link.setSlot(slot++);
            challengeRepositoryRepository.save(link);
        }
    }

    private GitHubRepositoryEntity requireAccessibleRepository(UUID actorId, UUID repositoryId) {
        GitHubRepositoryEntity repository = githubRepositoryRepository.findById(repositoryId)
                .filter(item -> item.getAccessStatus() == RepositoryAccessStatus.ACCESSIBLE)
                .orElseThrow(() -> new NotFoundException(ErrorCode.REPOSITORY_NOT_FOUND));
        GitHubAccountEntity account = githubAccountRepository.findByUserId(actorId)
                .orElseThrow(() -> new DomainException(ErrorCode.GITHUB_NOT_CONNECTED));
        Set<Long> installationIds = new HashSet<>();
        installationRepository.findAllByGithubAccountId(account.getId())
                .forEach(installation -> installationIds.add(installation.getInstallationId()));
        if (repository.getInstallationId() == null || !installationIds.contains(repository.getInstallationId())) {
            throw new NotFoundException(ErrorCode.REPOSITORY_NOT_FOUND);
        }
        return repository;
    }

    private void createMissingParticipants(ChallengeEntity challenge) {
        List<GroupMemberEntity> members = groupMemberRepository
                .findAllByGroupIdAndStatusOrderByJoinedAtAsc(challenge.getGroup().getId(), com.devrats.enums.MemberStatus.ACTIVE);
        for (GroupMemberEntity member : members) {
            participantRepository.findByChallengeIdAndUserId(challenge.getId(), member.getUser().getId())
                    .orElseGet(() -> {
                        ChallengeParticipantEntity participant = new ChallengeParticipantEntity();
                        participant.setChallenge(challenge);
                        participant.setUser(member.getUser());
                        participant.setGroupMember(member);
                        participant.setStatus(ParticipantStatus.ACTIVE);
                        return participantRepository.save(participant);
                    });
        }
    }

    private int nextSlot(UUID challengeId) {
        boolean[] used = new boolean[4];
        challengeRepositoryRepository.findAllByChallengeIdOrderBySlotAsc(challengeId)
                .forEach(link -> used[link.getSlot()] = true);
        for (int slot = 1; slot <= properties.getChallenges().getMaxRepositories(); slot++) {
            if (!used[slot]) {
                return slot;
            }
        }
        throw new ConflictException(ErrorCode.REPOSITORY_LIMIT_REACHED);
    }

    private void requireDraft(ChallengeEntity challenge) {
        if (challenge.getStatus() != ChallengeStatus.DRAFT) {
            throw new ConflictException(ErrorCode.INVALID_CHALLENGE_STATE,
                    "Alterações só são permitidas antes do início do desafio");
        }
    }

    private void notifyParticipants(ChallengeEntity challenge, NotificationType type, String dedupePrefix) {
        participantRepository.findAllByChallengeIdAndStatus(challenge.getId(), ParticipantStatus.ACTIVE)
                .forEach(participant -> notificationService.notify(participant.getUser().getId(), type,
                        challenge.getId(), dedupePrefix + ":" + participant.getUser().getId(), null));
    }

    private ChallengeResponse toResponse(ChallengeEntity challenge) {
        List<ChallengeRepositoryEntity> repositories = challengeRepositoryRepository
                .findAllByChallengeIdOrderBySlotAsc(challenge.getId());
        int participants = (int) participantRepository.countByChallengeIdAndStatus(challenge.getId(), ParticipantStatus.ACTIVE);
        return ChallengeViewHelper.toResponse(challenge, repositories, participants);
    }
}

package com.devrats.services.impl;

import com.devrats.config.AppProperties;
import com.devrats.dto.PageResponse;
import com.devrats.dto.StudyPhotoResponse;
import com.devrats.dto.UserDataExportResponse;
import com.devrats.entities.ChallengeEntity;
import com.devrats.entities.ChallengeParticipantEntity;
import com.devrats.entities.GitHubAccountEntity;
import com.devrats.entities.GitHubInstallationEntity;
import com.devrats.entities.GitHubRepositoryEntity;
import com.devrats.entities.GroupMemberEntity;
import com.devrats.entities.StudyPhotoEntity;
import com.devrats.enums.AuditAction;
import com.devrats.enums.ChallengeStatus;
import com.devrats.enums.GitHubConnectionStatus;
import com.devrats.enums.GroupRole;
import com.devrats.enums.NotificationType;
import com.devrats.enums.RepositoryAccessStatus;
import com.devrats.enums.VerificationMode;
import com.devrats.exceptions.ConflictException;
import com.devrats.exceptions.DomainException;
import com.devrats.exceptions.ErrorCode;
import com.devrats.exceptions.ForbiddenException;
import com.devrats.exceptions.NotFoundException;
import com.devrats.exceptions.ValidationException;
import com.devrats.repositories.GitHubAccountRepository;
import com.devrats.repositories.GitHubInstallationRepository;
import com.devrats.repositories.GitHubRepositoryRepository;
import com.devrats.repositories.MemberDailyActivityRepository;
import com.devrats.repositories.StudyPhotoRepository;
import com.devrats.services.AuditService;
import com.devrats.services.ChallengeCompletionService;
import com.devrats.services.GroupAccessService;
import com.devrats.services.NotificationService;
import com.devrats.services.ProgressService;
import com.devrats.services.StudyPhotoService;
import com.devrats.services.storage.StudyPhotoStorage;
import com.devrats.services.study.StudyPhotoCommand;
import com.devrats.utils.ActivityDateHelper;
import com.devrats.utils.ChatImageProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class StudyPhotoServiceImpl implements StudyPhotoService {

    private static final Logger log = LoggerFactory.getLogger(StudyPhotoServiceImpl.class);
    private static final int MAX_PAGE_SIZE = 50;
    private static final Set<Integer> MILESTONES = Set.of(7, 14, 30, 60, 100, 365);
    private static final DateTimeFormatter KEY_DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final Pattern COMMIT_URL = Pattern.compile(
            "^https://github\\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)/commit/[0-9a-fA-F]{7,40}$");

    private final StudyPhotoRepository photoRepository;
    private final MemberDailyActivityRepository activityRepository;
    private final GitHubRepositoryRepository githubRepositoryRepository;
    private final GitHubAccountRepository githubAccountRepository;
    private final GitHubInstallationRepository installationRepository;
    private final GroupAccessService accessService;
    private final StudyPhotoStorage storage;
    private final ProgressService progressService;
    private final ChallengeCompletionService completionService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final AppProperties properties;
    private final Clock clock;

    public StudyPhotoServiceImpl(StudyPhotoRepository photoRepository,
                                 MemberDailyActivityRepository activityRepository,
                                 GitHubRepositoryRepository githubRepositoryRepository,
                                 GitHubAccountRepository githubAccountRepository,
                                 GitHubInstallationRepository installationRepository,
                                 GroupAccessService accessService,
                                 StudyPhotoStorage storage,
                                 ProgressService progressService,
                                 ChallengeCompletionService completionService,
                                 NotificationService notificationService,
                                 AuditService auditService,
                                 AppProperties properties,
                                 Clock clock) {
        this.photoRepository = photoRepository;
        this.activityRepository = activityRepository;
        this.githubRepositoryRepository = githubRepositoryRepository;
        this.githubAccountRepository = githubAccountRepository;
        this.installationRepository = installationRepository;
        this.accessService = accessService;
        this.storage = storage;
        this.progressService = progressService;
        this.completionService = completionService;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public StudyPhotoResponse publish(UUID challengeId, UUID userId, StudyPhotoCommand command) {
        ChallengeParticipantEntity participant = accessService.requireParticipant(challengeId, userId);
        ChallengeEntity challenge = participant.getChallenge();
        requirePhotoMode(challenge);
        if (!storage.isAvailable()) {
            throw new DomainException(ErrorCode.PHOTO_STORAGE_UNAVAILABLE,
                    "O armazenamento de fotos não está configurado");
        }

        Instant now = clock.instant();
        ZoneId zone = ActivityDateHelper.parseZone(challenge.getTimezone());
        LocalDate activityDate = ActivityDateHelper.toActivityDate(now, zone);
        validateWindow(challenge, activityDate, zone);

        if (photoRepository.existsByChallengeIdAndParticipantIdAndActivityDate(
                challengeId, participant.getId(), activityDate)) {
            throw new ConflictException(ErrorCode.PHOTO_ALREADY_PUBLISHED,
                    "Você já publicou a foto de hoje");
        }

        String title = normalizeText(command.title(), 120, "O título");
        String description = normalizeText(command.description(), 500, "A descrição");
        GitHubRepositoryEntity repository = resolveRepository(userId, command.githubRepositoryId());
        String commitUrl = validateCommitUrl(command.commitUrl(), repository);
        ChatImageProcessor.ProcessedImage image = processImage(command.file());

        StudyPhotoEntity photo = new StudyPhotoEntity();
        photo.setChallenge(challenge);
        photo.setParticipant(participant);
        photo.setUser(participant.getUser());
        photo.setActivityDate(activityDate);
        photo.setContentType("image/jpeg");
        photo.setSizeBytes(image.bytes().length);
        photo.setWidth(image.width());
        photo.setHeight(image.height());
        photo.setTitle(title);
        photo.setDescription(description);
        photo.setGithubRepository(repository);
        photo.setCommitUrl(commitUrl);
        //---- A object key precisa existir antes do INSERT (coluna NOT NULL); o UUID do path
        //---- é independente do id gerado pelo Hibernate para permitir o upload antes do flush.
        String objectKey = objectKey(challengeId, userId, UUID.randomUUID(), now);
        photo.setObjectKey(objectKey);
        storage.upload(objectKey, image.bytes(), photo.getContentType());
        registerRollbackCleanup(objectKey);
        photoRepository.save(photo);

        int inserted = activityRepository.insertPhotoDayIfAbsent(challengeId, participant.getId(), userId,
                activityDate, now);
        if (inserted == 0) {
            //---- Pode existir um dia de commit no mesmo desafio (dados legados/híbrido futuro).
            int updated = activityRepository.incrementPhoto(challengeId, participant.getId(), activityDate, now);
            if (updated == 0) {
                throw new ConflictException(ErrorCode.PHOTO_ALREADY_PUBLISHED,
                        "Você já publicou a foto de hoje");
            }
        }

        progressService.recalculateParticipant(participant.getId());
        notifyDailyGoal(challenge, participant, activityDate);
        auditService.record(userId, "CHALLENGE", challengeId, AuditAction.PHOTO_PUBLISHED,
                Map.of("activityDate", activityDate.toString(), "photoId", photo.getId().toString()));
        completionService.checkAndComplete(challengeId);
        return toResponse(photo, userId, true);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StudyPhotoResponse> list(UUID challengeId, UUID actorId, UUID targetUserId, LocalDate date,
                                                 int page, int size) {
        ChallengeEntity challenge = accessService.requireChallengeMember(challengeId, actorId);
        GroupMemberEntity member = accessService.requireMember(challenge.getGroup().getId(), actorId);
        boolean owner = member.getRole() == GroupRole.OWNER;
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));

        Page<StudyPhotoEntity> result;
        if (targetUserId != null && date != null) {
            result = photoRepository.findAllByChallengeIdAndUserIdAndActivityDateOrderByCreatedAtAsc(
                    challengeId, targetUserId, date, pageable);
        } else if (targetUserId != null) {
            result = photoRepository.findAllByChallengeIdAndUserIdOrderByActivityDateDescCreatedAtDesc(
                    challengeId, targetUserId, pageable);
        } else if (date != null) {
            result = photoRepository.findAllByChallengeIdAndActivityDateOrderByCreatedAtAsc(
                    challengeId, date, pageable);
        } else {
            result = photoRepository.findAllByChallengeIdOrderByActivityDateDescCreatedAtDesc(challengeId, pageable);
        }
        return PageResponse.from(result.map(photo -> toResponse(photo, actorId,
                owner || photo.getUser().getId().equals(actorId))));
    }

    @Override
    @Transactional(readOnly = true)
    public StudyPhotoResponse refreshUrl(UUID challengeId, UUID actorId, UUID photoId) {
        ChallengeEntity challenge = accessService.requireChallengeMember(challengeId, actorId);
        GroupMemberEntity member = accessService.requireMember(challenge.getGroup().getId(), actorId);
        StudyPhotoEntity photo = photoRepository.findByIdAndChallengeId(photoId, challengeId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.PHOTO_NOT_FOUND));
        boolean canDelete = member.getRole() == GroupRole.OWNER || photo.getUser().getId().equals(actorId);
        return toResponse(photo, actorId, canDelete);
    }

    @Override
    @Transactional
    public void delete(UUID challengeId, UUID actorId, UUID photoId) {
        ChallengeEntity challenge = accessService.requireChallengeMember(challengeId, actorId);
        GroupMemberEntity member = accessService.requireMember(challenge.getGroup().getId(), actorId);
        StudyPhotoEntity photo = photoRepository.findByIdAndChallengeId(photoId, challengeId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.PHOTO_NOT_FOUND));
        boolean owner = member.getRole() == GroupRole.OWNER;
        if (!owner && !photo.getUser().getId().equals(actorId)) {
            throw new ForbiddenException("Você só pode excluir suas próprias fotos");
        }
        if (challenge.getStatus() != ChallengeStatus.ACTIVE) {
            throw new ConflictException(ErrorCode.INVALID_CHALLENGE_STATE,
                    "Só é possível excluir fotos com o desafio ativo");
        }

        UUID participantId = photo.getParticipant().getId();
        LocalDate activityDate = photo.getActivityDate();
        String objectKey = photo.getObjectKey();
        photoRepository.delete(photo);
        removePhotoFromDay(challenge, participantId, activityDate);
        progressService.recalculateParticipant(participantId);
        afterCommit(() -> storage.delete(objectKey));
        auditService.record(actorId, "CHALLENGE", challengeId, AuditAction.PHOTO_DELETED,
                Map.of("activityDate", activityDate.toString(), "photoId", photoId.toString()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDataExportResponse.StudyPhotoExport> exportForUser(UUID userId) {
        return photoRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(photo -> new UserDataExportResponse.StudyPhotoExport(photo.getId(),
                        photo.getChallenge().getId(), photo.getActivityDate(), photo.getTitle(),
                        photo.getDescription(), photo.getCreatedAt()))
                .toList();
    }

    @Override
    @Transactional
    public void deleteAllForUser(UUID userId) {
        List<StudyPhotoEntity> photos = photoRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
        if (photos.isEmpty()) {
            return;
        }
        List<String> objectKeys = photos.stream().map(StudyPhotoEntity::getObjectKey).toList();
        Set<UUID> participantIds = new HashSet<>();
        for (StudyPhotoEntity photo : photos) {
            participantIds.add(photo.getParticipant().getId());
            removePhotoFromDay(photo.getChallenge(), photo.getParticipant().getId(),
                    photo.getActivityDate());
            photoRepository.delete(photo);
        }
        participantIds.forEach(progressService::recalculateParticipant);
        afterCommit(() -> objectKeys.forEach(storage::delete));
    }

    private void requirePhotoMode(ChallengeEntity challenge) {
        if (challenge.getVerificationMode() != VerificationMode.STUDY_PHOTO) {
            throw new ConflictException(ErrorCode.INVALID_CHALLENGE_STATE,
                    "Este desafio não aceita fotos de estudo");
        }
        if (challenge.getStatus() != ChallengeStatus.ACTIVE) {
            throw new ConflictException(ErrorCode.INVALID_CHALLENGE_STATE,
                    "O desafio não está ativo");
        }
    }

    private void validateWindow(ChallengeEntity challenge, LocalDate activityDate, ZoneId zone) {
        LocalDate start = ActivityDateHelper.toActivityDate(challenge.getStartsAt(), zone);
        if (activityDate.isBefore(start)) {
            throw new ConflictException(ErrorCode.INVALID_CHALLENGE_STATE,
                    "O desafio ainda não começou");
        }
        if (challenge.getEndsAt() != null
                && activityDate.isAfter(ActivityDateHelper.toActivityDate(challenge.getEndsAt(), zone))) {
            throw new ConflictException(ErrorCode.INVALID_CHALLENGE_STATE,
                    "O desafio já foi encerrado");
        }
    }

    private String normalizeText(String value, int maxLength, String field) {
        if (value == null) {
            return null;
        }
        String normalized = value.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "").trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new ValidationException(field + " deve ter no máximo " + maxLength + " caracteres");
        }
        return normalized;
    }

    private GitHubRepositoryEntity resolveRepository(UUID userId, UUID repositoryId) {
        if (repositoryId == null) {
            return null;
        }
        GitHubRepositoryEntity repository = githubRepositoryRepository.findById(repositoryId)
                .filter(item -> item.getAccessStatus() == RepositoryAccessStatus.ACCESSIBLE)
                .orElseThrow(() -> new NotFoundException(ErrorCode.REPOSITORY_NOT_FOUND));
        GitHubAccountEntity account = githubAccountRepository.findByUserId(userId)
                .filter(item -> item.getConnectionStatus() == GitHubConnectionStatus.CONNECTED)
                .orElseThrow(() -> new DomainException(ErrorCode.GITHUB_NOT_CONNECTED));
        Set<Long> installationIds = installationRepository.findAllByGithubAccountId(account.getId()).stream()
                .map(GitHubInstallationEntity::getInstallationId)
                .collect(Collectors.toSet());
        if (repository.getInstallationId() == null
                || !installationIds.contains(repository.getInstallationId())) {
            throw new NotFoundException(ErrorCode.REPOSITORY_NOT_FOUND);
        }
        return repository;
    }

    private String validateCommitUrl(String commitUrl, GitHubRepositoryEntity repository) {
        if (commitUrl == null || commitUrl.isBlank()) {
            return null;
        }
        String value = commitUrl.trim();
        if (value.length() > 500) {
            throw new ValidationException("URL do commit inválida");
        }
        Matcher matcher = COMMIT_URL.matcher(value);
        if (!matcher.matches()) {
            throw new ValidationException("Informe uma URL de commit do GitHub");
        }
        if (repository != null) {
            String fullName = matcher.group(1) + "/" + matcher.group(2);
            if (!fullName.equalsIgnoreCase(repository.getFullName())) {
                throw new ValidationException("O commit não pertence ao repositório selecionado");
            }
        }
        return value;
    }

    private ChatImageProcessor.ProcessedImage processImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new DomainException(ErrorCode.PHOTO_INVALID_FORMAT, "Envie uma foto");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new DomainException(ErrorCode.PHOTO_INVALID_FORMAT, "Não foi possível ler a foto");
        }
        try {
            return ChatImageProcessor.process(content, properties.getStudyPhotos().getMaxImageBytes());
        } catch (DomainException exception) {
            if (exception.getErrorCode() == ErrorCode.ATTACHMENT_TOO_LARGE) {
                throw new DomainException(ErrorCode.PHOTO_TOO_LARGE, exception.getMessage());
            }
            throw new DomainException(ErrorCode.PHOTO_INVALID_FORMAT, exception.getMessage());
        }
    }

    private void notifyDailyGoal(ChallengeEntity challenge, ChallengeParticipantEntity participant,
                                 LocalDate activityDate) {
        UUID userId = participant.getUser().getId();
        notificationService.notify(userId, NotificationType.DAILY_GOAL, challenge.getId(),
                "daily-goal:" + challenge.getId() + ":" + participant.getId() + ":" + activityDate,
                Map.of("activityDate", activityDate.toString()));
        int validDays = participant.getValidDays();
        if (MILESTONES.contains(validDays)) {
            notificationService.notify(userId, NotificationType.MILESTONE, challenge.getId(),
                    "milestone:" + challenge.getId() + ":" + participant.getId() + ":" + validDays,
                    Map.of("validDays", String.valueOf(validDays)));
        }
    }

    private void removePhotoFromDay(ChallengeEntity challenge, UUID participantId, LocalDate activityDate) {
        activityRepository.findByChallengeIdAndParticipantIdAndActivityDate(challenge.getId(), participantId, activityDate)
                .ifPresent(day -> {
                    //---- No modo foto o dia só existe pela foto; commit residual nunca o mantém.
                    if (!challenge.getVerificationMode().countsCommits() || day.getValidCommitCount() == 0) {
                        activityRepository.delete(day);
                        return;
                    }
                    int remainingPhotos = Math.max(0, day.getPhotoCount() - 1);
                    day.setPhotoCount(remainingPhotos);
                    if (remainingPhotos == 0) {
                        day.setFirstPhotoAt(null);
                    }
                    activityRepository.save(day);
                });
    }

    private StudyPhotoResponse toResponse(StudyPhotoEntity photo, UUID actorId, boolean canDelete) {
        String imageUrl = null;
        Instant expiresAt = null;
        if (storage.isAvailable() && photo.getObjectKey() != null) {
            imageUrl = storage.presignGet(photo.getObjectKey(), properties.getStudyPhotos().getUrlTtl(),
                    "inline", photo.getContentType());
            expiresAt = clock.instant().plus(properties.getStudyPhotos().getUrlTtl());
        }
        return new StudyPhotoResponse(photo.getId(), photo.getChallenge().getId(),
                photo.getUser().getId(), photo.getUser().getDisplayName(), photo.getUser().getUsername(),
                photo.getUser().getAvatarUrl(), photo.getActivityDate(), photo.getTitle(),
                photo.getDescription(), imageUrl, expiresAt, photo.getWidth(), photo.getHeight(),
                photo.getGithubRepository() == null ? null : photo.getGithubRepository().getFullName(),
                photo.getCommitUrl(), photo.getCreatedAt(), canDelete);
    }

    private String objectKey(UUID challengeId, UUID userId, UUID photoId, Instant now) {
        String prefix = properties.getStudyPhotos().getStorage().getKeyPrefix();
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "study-photos" : prefix.replaceAll("^/+", "").replaceAll("/+$", "");
        ZonedDateTime date = ZonedDateTime.ofInstant(now, ZoneOffset.UTC);
        return normalizedPrefix + "/" + challengeId + "/" + userId + "/" + KEY_DATE.format(date) + "/"
                + photoId + ".jpg";
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private void registerRollbackCleanup(String objectKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    storage.delete(objectKey);
                }
            }
        });
    }
}

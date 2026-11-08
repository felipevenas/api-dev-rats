package com.devrats.repositories;

import com.devrats.entities.StudyPhotoEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudyPhotoRepository extends JpaRepository<StudyPhotoEntity, UUID> {

    Optional<StudyPhotoEntity> findByIdAndChallengeId(UUID id, UUID challengeId);

    boolean existsByChallengeIdAndParticipantIdAndActivityDate(UUID challengeId, UUID participantId,
                                                               LocalDate activityDate);

    Optional<StudyPhotoEntity> findByChallengeIdAndParticipantIdAndActivityDate(UUID challengeId, UUID participantId,
                                                                               LocalDate activityDate);

    List<StudyPhotoEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    @EntityGraph(attributePaths = "user")
    Page<StudyPhotoEntity> findAllByChallengeIdOrderByActivityDateDescCreatedAtDesc(UUID challengeId, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Page<StudyPhotoEntity> findAllByChallengeIdAndUserIdOrderByActivityDateDescCreatedAtDesc(UUID challengeId,
                                                                                             UUID userId,
                                                                                             Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Page<StudyPhotoEntity> findAllByChallengeIdAndActivityDateOrderByCreatedAtAsc(UUID challengeId,
                                                                                 LocalDate activityDate,
                                                                                 Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Page<StudyPhotoEntity> findAllByChallengeIdAndUserIdAndActivityDateOrderByCreatedAtAsc(UUID challengeId,
                                                                                           UUID userId,
                                                                                           LocalDate activityDate,
                                                                                           Pageable pageable);
}

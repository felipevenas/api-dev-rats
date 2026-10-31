package com.devrats.repositories;

import com.devrats.entities.MemberDailyActivityEntity;
import com.devrats.enums.ActivityStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MemberDailyActivityRepository extends JpaRepository<MemberDailyActivityEntity, UUID> {

    Optional<MemberDailyActivityEntity> findByChallengeIdAndParticipantIdAndActivityDate(UUID challengeId, UUID participantId, LocalDate activityDate);

    List<MemberDailyActivityEntity> findAllByChallengeIdAndParticipantIdAndValidationStatusOrderByActivityDateAsc(UUID challengeId, UUID participantId, ActivityStatus status);

    List<MemberDailyActivityEntity> findAllByChallengeIdAndValidationStatusOrderByActivityDateAsc(UUID challengeId, ActivityStatus status);

    long countByChallengeIdAndParticipantIdAndValidationStatus(UUID challengeId, UUID participantId, ActivityStatus status);

    @Query("SELECT a.activityDate FROM MemberDailyActivityEntity a " +
            "WHERE a.challenge.id = :challengeId AND a.participant.id = :participantId AND a.validationStatus = 'VALID' " +
            "ORDER BY a.activityDate ASC")
    List<LocalDate> findValidDates(@Param("challengeId") UUID challengeId, @Param("participantId") UUID participantId);

    @Query("SELECT a.firstEligibleCommitAt FROM MemberDailyActivityEntity a " +
            "WHERE a.challenge.id = :challengeId AND a.participant.id = :participantId " +
            "AND a.activityDate = :activityDate AND a.validationStatus = 'VALID'")
    Optional<Instant> findFirstEligibleCommitAt(@Param("challengeId") UUID challengeId,
                                                @Param("participantId") UUID participantId,
                                                @Param("activityDate") LocalDate activityDate);

    //---- Insert first, then increment: the unique key is the concurrency guard for same-day commits.
    @Modifying
    @Query(value = """
            INSERT INTO member_daily_activity
                (id, challenge_id, participant_id, user_id, activity_date, first_eligible_commit_at,
                 valid_commit_count, validation_status, validated_at)
            VALUES
                (gen_random_uuid(), :challengeId, :participantId, :userId, :activityDate, :commitAt,
                 1, 'VALID', now())
            ON CONFLICT (challenge_id, participant_id, activity_date) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("challengeId") UUID challengeId,
                       @Param("participantId") UUID participantId,
                       @Param("userId") UUID userId,
                       @Param("activityDate") LocalDate activityDate,
                       @Param("commitAt") Instant commitAt);

    @Modifying
    @Query(value = """
            UPDATE member_daily_activity
            SET valid_commit_count = valid_commit_count + 1,
                first_eligible_commit_at = LEAST(first_eligible_commit_at, :commitAt)
            WHERE challenge_id = :challengeId AND participant_id = :participantId AND activity_date = :activityDate
            """, nativeQuery = true)
    int increment(@Param("challengeId") UUID challengeId,
                  @Param("participantId") UUID participantId,
                  @Param("activityDate") LocalDate activityDate,
                  @Param("commitAt") Instant commitAt);
}

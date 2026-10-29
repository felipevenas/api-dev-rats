package com.devstreak.entities;

import com.devstreak.enums.ActivityStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "member_daily_activity", uniqueConstraints = @UniqueConstraint(name = "uq_daily_activity_participant_date", columnNames = {"challenge_id", "participant_id", "activity_date"}))
@Getter
@Setter
@NoArgsConstructor
public class MemberDailyActivityEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "challenge_id", nullable = false)
    private ChallengeEntity challenge;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_id", nullable = false)
    private ChallengeParticipantEntity participant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "activity_date", nullable = false)
    private LocalDate activityDate;

    @Column(name = "first_eligible_commit_at", nullable = false)
    private Instant firstEligibleCommitAt;

    @Column(name = "valid_commit_count", nullable = false)
    private int validCommitCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", nullable = false, length = 16)
    private ActivityStatus validationStatus = ActivityStatus.VALID;

    @CreationTimestamp
    @Column(name = "validated_at", nullable = false, updatable = false)
    private Instant validatedAt;

    @Column(name = "correction_reason", length = 255)
    private String correctionReason;
}

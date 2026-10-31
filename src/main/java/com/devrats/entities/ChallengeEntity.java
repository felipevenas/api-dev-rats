package com.devrats.entities;

import com.devrats.enums.ChallengeStatus;
import com.devrats.enums.VerificationMode;
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
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "challenges", uniqueConstraints = @UniqueConstraint(name = "uq_challenges_group_sequence", columnNames = {"group_id", "sequence_no"}))
@Getter
@Setter
@NoArgsConstructor
public class ChallengeEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private GroupEntity group;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_mode", nullable = false, length = 32)
    private VerificationMode verificationMode;

    @Column(name = "target_valid_days", nullable = false)
    private int targetValidDays;

    @Column(nullable = false, length = 64)
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ChallengeStatus status = ChallengeStatus.DRAFT;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "admission_ends_at")
    private Instant admissionEndsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "allow_forks", nullable = false)
    private boolean allowForks;

    @Column(name = "tiebreak_rule", nullable = false, length = 120)
    private String tiebreakRule;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private UserEntity createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

package com.devrats.entities;

import com.devrats.enums.ParticipantStatus;
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
@Table(name = "challenge_participants", uniqueConstraints = @UniqueConstraint(name = "uq_participants_challenge_user", columnNames = {"challenge_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
public class ChallengeParticipantEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "challenge_id", nullable = false)
    private ChallengeEntity challenge;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_member_id", nullable = false)
    private GroupMemberEntity groupMember;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ParticipantStatus status = ParticipantStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    @Column(name = "valid_days", nullable = false)
    private int validDays;

    @Column(name = "current_streak", nullable = false)
    private int currentStreak;

    @Column(name = "best_streak", nullable = false)
    private int bestStreak;

    @Column(name = "last_valid_date")
    private LocalDate lastValidDate;

    @Column(name = "completed_at")
    private Instant completedAt;
}

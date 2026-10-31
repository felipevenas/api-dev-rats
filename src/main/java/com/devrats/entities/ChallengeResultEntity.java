package com.devrats.entities;

import com.devrats.enums.ResultStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "challenge_results")
@Getter
@Setter
@NoArgsConstructor
public class ChallengeResultEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(nullable = false, updatable = false)
    private UUID id;

    //---- One official result per challenge; the unique constraint is the final winner guard.
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "challenge_id", nullable = false, unique = true)
    private ChallengeEntity challenge;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "winner_user_id", nullable = false)
    private UserEntity winnerUser;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "winner_participant_id", nullable = false)
    private ChallengeParticipantEntity winnerParticipant;

    @Column(name = "achieved_at", nullable = false)
    private Instant achievedAt;

    @Column(name = "winning_activity_date", nullable = false)
    private LocalDate winningActivityDate;

    @Column(name = "valid_days_at_win", nullable = false)
    private int validDaysAtWin;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_status", nullable = false, length = 16)
    private ResultStatus resultStatus = ResultStatus.FINAL;

    @CreationTimestamp
    @Column(name = "finalized_at", nullable = false, updatable = false)
    private Instant finalizedAt;

    @Column(name = "superseded_by_result_id")
    private UUID supersededByResultId;
}

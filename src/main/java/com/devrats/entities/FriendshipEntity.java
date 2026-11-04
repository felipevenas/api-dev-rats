package com.devrats.entities;

import com.devrats.enums.FriendshipStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Amizade ou solicitação entre dois usuários.
 *
 * <p>O índice único {@code uq_friendships_pair} usa {@code LEAST/GREATEST},
 * então existe no máximo uma linha por par, qualquer que seja a direção.
 * Datas são escritas pelo serviço com o {@code Clock} injetado — inclusive
 * {@code created_at}, porque reabrir uma solicitação recusada precisa renovar
 * o instante usado na chave de deduplicação da notificação.</p>
 */
@Entity
@Table(name = "friendships")
@Getter
@Setter
@NoArgsConstructor
public class FriendshipEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private UserEntity requester;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "addressee_id", nullable = false)
    private UserEntity addressee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FriendshipStatus status = FriendshipStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "responded_at")
    private Instant respondedAt;
}

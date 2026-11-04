package com.devrats.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Último heartbeat de um usuário.
 *
 * <p>Tabela separada de {@code users} para não tocar {@code users.updated_at}
 * a cada batida. O identificador é o próprio {@code user_id} (sem gerador).</p>
 */
@Entity
@Table(name = "user_presence")
@Getter
@Setter
@NoArgsConstructor
public class UserPresenceEntity {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;
}

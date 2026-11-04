package com.devrats.enums;

/**
 * Presença calculada a partir do último heartbeat.
 *
 * <p>O instante cru ({@code last_seen_at}) nunca sai da API: apenas
 * {@code ONLINE}, {@code AWAY} ou {@code OFFLINE}.</p>
 */
public enum PresenceStatus {
    ONLINE,
    AWAY,
    OFFLINE
}

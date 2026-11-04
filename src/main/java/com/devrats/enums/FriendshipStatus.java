package com.devrats.enums;

/**
 * Estados persistidos de uma amizade (ou solicitação) entre dois usuários.
 *
 * <p>O par é único independente da direção; a direção vive em
 * {@code requester_id}/{@code addressee_id} e só é usada para autorização.
 * A leitura pública usa {@link FriendshipStatusView}, que colapsa
 * {@code DECLINED}/{@code CANCELLED} em {@code NONE}.</p>
 */
public enum FriendshipStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    CANCELLED
}

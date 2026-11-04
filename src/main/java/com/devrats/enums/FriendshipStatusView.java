package com.devrats.enums;

/**
 * Status de amizade do ponto de vista de quem consulta.
 *
 * <p>Não confundir com {@link FriendshipStatus}, que é o estado persistido.
 * Aqui {@code DECLINED}/{@code CANCELLED}/inexistente viram {@code NONE} para
 * não revelar recusas.</p>
 */
public enum FriendshipStatusView {
    NONE,
    PENDING_OUTGOING,
    PENDING_INCOMING,
    FRIENDS,
    SELF
}

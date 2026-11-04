package com.devrats.services;

import com.devrats.dto.FriendRequestResponse;
import com.devrats.dto.FriendResponse;
import com.devrats.dto.FriendshipStatusResponse;
import com.devrats.dto.SendFriendRequestRequest;
import com.devrats.enums.FriendRequestDirection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Máquina de estados de amizades.
 *
 * <p>Toda transição valida o ator no {@code WHERE} da consulta: quem não
 * participa recebe 404 (nunca 403) e quem participa em estado inválido recebe 409.</p>
 */
public interface FriendService {

    Page<FriendResponse> listFriends(UUID userId, Pageable pageable);

    Page<FriendRequestResponse> listRequests(UUID userId, FriendRequestDirection direction, Pageable pageable);

    /**
     * Status de amizade em lote (máximo 50 ids). Ids desconhecidos e estados
     * {@code DECLINED}/{@code CANCELLED} retornam {@code NONE}.
     */
    List<FriendshipStatusResponse> statuses(UUID userId, List<UUID> userIds);

    FriendRequestResponse sendRequest(UUID userId, SendFriendRequestRequest request);

    FriendRequestResponse acceptRequest(UUID userId, UUID requestId);

    FriendRequestResponse declineRequest(UUID userId, UUID requestId);

    void cancelRequest(UUID userId, UUID requestId);

    void removeFriend(UUID userId, UUID friendUserId);
}

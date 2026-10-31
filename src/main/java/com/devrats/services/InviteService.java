package com.devrats.services;

import com.devrats.dto.CreateInviteRequest;
import com.devrats.dto.GroupResponse;
import com.devrats.dto.InvitePreviewResponse;
import com.devrats.dto.InviteResponse;

import java.util.List;
import java.util.UUID;

public interface InviteService {

    InviteResponse createInvite(UUID groupId, UUID actorId, CreateInviteRequest request);

    List<InviteResponse> listInvites(UUID groupId, UUID actorId);

    void revokeInvite(UUID groupId, UUID inviteId, UUID actorId);

    InvitePreviewResponse preview(String token);

    GroupResponse accept(String token, UUID userId);
}

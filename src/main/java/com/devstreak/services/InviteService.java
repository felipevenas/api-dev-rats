package com.devstreak.services;

import com.devstreak.dto.CreateInviteRequest;
import com.devstreak.dto.GroupResponse;
import com.devstreak.dto.InvitePreviewResponse;
import com.devstreak.dto.InviteResponse;

import java.util.List;
import java.util.UUID;

public interface InviteService {

    InviteResponse createInvite(UUID groupId, UUID actorId, CreateInviteRequest request);

    List<InviteResponse> listInvites(UUID groupId, UUID actorId);

    void revokeInvite(UUID groupId, UUID inviteId, UUID actorId);

    InvitePreviewResponse preview(String token);

    GroupResponse accept(String token, UUID userId);
}

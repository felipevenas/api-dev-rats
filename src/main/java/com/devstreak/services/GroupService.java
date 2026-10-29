package com.devstreak.services;

import com.devstreak.dto.CreateGroupRequest;
import com.devstreak.dto.GroupMemberResponse;
import com.devstreak.dto.GroupResponse;
import com.devstreak.dto.UpdateGroupRequest;

import java.util.List;
import java.util.UUID;

public interface GroupService {

    GroupResponse createGroup(UUID creatorId, CreateGroupRequest request);

    List<GroupResponse> listMyGroups(UUID userId);

    GroupResponse getGroup(UUID groupId, UUID userId);

    GroupResponse updateGroup(UUID groupId, UUID userId, UpdateGroupRequest request);

    List<GroupMemberResponse> listMembers(UUID groupId, UUID userId);

    void removeMember(UUID groupId, UUID actorId, UUID memberUserId);

    void leaveGroup(UUID groupId, UUID userId);
}

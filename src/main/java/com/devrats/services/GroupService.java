package com.devrats.services;

import com.devrats.dto.CreateGroupRequest;
import com.devrats.dto.GroupMemberResponse;
import com.devrats.dto.GroupResponse;
import com.devrats.dto.UpdateGroupRequest;

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

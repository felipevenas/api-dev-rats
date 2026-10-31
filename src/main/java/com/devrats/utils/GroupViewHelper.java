package com.devrats.utils;

import com.devrats.dto.GroupMemberResponse;
import com.devrats.dto.GroupResponse;
import com.devrats.entities.GroupEntity;
import com.devrats.entities.GroupMemberEntity;
import com.devrats.entities.UserEntity;
import com.devrats.dto.ChallengeResponse;

public final class GroupViewHelper {

    private GroupViewHelper() {
    }

    public static GroupResponse toResponse(GroupEntity group, ChallengeResponse currentChallenge) {
        return new GroupResponse(group.getId(), group.getName(), group.getDescription(), group.getCreator().getId(),
                group.getTimezone(), group.getMaxMembers(), group.getStatus(), group.getCreatedAt(), currentChallenge);
    }

    public static GroupMemberResponse toMemberResponse(GroupMemberEntity member) {
        UserEntity user = member.getUser();
        return new GroupMemberResponse(member.getId(), user.getId(), user.getDisplayName(), user.getUsername(),
                user.getAvatarUrl(), member.getRole(), member.getStatus(), member.getJoinedAt());
    }
}

package com.devstreak.utils;

import com.devstreak.dto.GroupMemberResponse;
import com.devstreak.dto.GroupResponse;
import com.devstreak.entities.GroupEntity;
import com.devstreak.entities.GroupMemberEntity;
import com.devstreak.entities.UserEntity;
import com.devstreak.dto.ChallengeResponse;

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

package com.devrats.repositories;

import com.devrats.entities.ConversationEntity;
import com.devrats.entities.GroupEntity;
import com.devrats.enums.ConversationType;
import com.devrats.enums.FriendshipStatus;
import com.devrats.enums.GroupStatus;
import com.devrats.enums.MemberStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<ConversationEntity, UUID> {

    Optional<ConversationEntity> findByDirectKey(String directKey);

    Optional<ConversationEntity> findByGroupId(UUID groupId);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO conversations
                (id, type, user_a_id, user_b_id, direct_key, last_message_at, created_at, updated_at)
            VALUES (:id, 'DIRECT', :userA, :userB, :directKey, :now, :now, :now)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int insertDirectIfAbsent(@Param("id") UUID id,
                             @Param("userA") UUID userA,
                             @Param("userB") UUID userB,
                             @Param("directKey") String directKey,
                             @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO conversations
                (id, type, group_id, last_message_at, created_at, updated_at)
            VALUES (:id, 'GROUP', :groupId, :now, :now, :now)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int insertGroupIfAbsent(@Param("id") UUID id,
                            @Param("groupId") UUID groupId,
                            @Param("now") Instant now);

    @EntityGraph(attributePaths = {"userA", "userB", "group"})
    @Query("""
            select c from ConversationEntity c
            where (
                c.type = :direct
                and (c.userA.id = :userId or c.userB.id = :userId)
                and exists (
                    select f.id from FriendshipEntity f
                    where f.status = :accepted
                      and (
                        (f.requester.id = c.userA.id and f.addressee.id = c.userB.id)
                        or (f.requester.id = c.userB.id and f.addressee.id = c.userA.id)
                      )
                )
            ) or (
                c.type = :group
                and exists (
                    select g.id from GroupEntity g
                    where g.id = c.group.id and g.status = :activeGroup
                )
                and exists (
                    select gm.id from GroupMemberEntity gm
                    where gm.group.id = c.group.id
                      and gm.user.id = :userId
                      and gm.status = :activeMember
                )
            )
            order by c.lastMessageAt desc
            """)
    Page<ConversationEntity> findVisibleForUser(@Param("userId") UUID userId,
                                                @Param("direct") ConversationType direct,
                                                @Param("group") ConversationType group,
                                                @Param("activeGroup") GroupStatus activeGroup,
                                                @Param("accepted") FriendshipStatus accepted,
                                                @Param("activeMember") MemberStatus activeMember,
                                                Pageable pageable);
}

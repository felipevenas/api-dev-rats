package com.devrats.repositories;

import com.devrats.entities.GroupMemberEntity;
import com.devrats.enums.GroupRole;
import com.devrats.enums.MemberStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupMemberRepository extends JpaRepository<GroupMemberEntity, UUID> {

    Optional<GroupMemberEntity> findByGroupIdAndUserId(UUID groupId, UUID userId);

    List<GroupMemberEntity> findAllByGroupIdAndStatusOrderByJoinedAtAsc(UUID groupId, MemberStatus status);

    List<GroupMemberEntity> findAllByUserIdAndStatus(UUID userId, MemberStatus status);

    List<GroupMemberEntity> findAllByUserId(UUID userId);

    boolean existsByGroupIdAndUserIdAndStatus(UUID groupId, UUID userId, MemberStatus status);

    long countByGroupIdAndStatus(UUID groupId, MemberStatus status);

    Optional<GroupMemberEntity> findByGroupIdAndRoleAndStatus(UUID groupId, GroupRole role, MemberStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM GroupMemberEntity m WHERE m.id = :id")
    Optional<GroupMemberEntity> findByIdForUpdate(@Param("id") UUID id);
}

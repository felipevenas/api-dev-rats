package com.devrats.repositories;

import com.devrats.entities.GroupEntity;
import com.devrats.enums.GroupStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupRepository extends JpaRepository<GroupEntity, UUID> {

    Optional<GroupEntity> findByIdAndStatus(UUID id, GroupStatus status);

    @Query("SELECT g FROM GroupEntity g WHERE g.id IN " +
            "(SELECT m.group.id FROM GroupMemberEntity m WHERE m.user.id = :userId AND m.status = 'ACTIVE') " +
            "ORDER BY g.createdAt DESC")
    List<GroupEntity> findAllByMemberUserId(@Param("userId") UUID userId);

    //---- Serializes invite acceptance so the max_members check cannot be bypassed by concurrent invites.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM GroupEntity g WHERE g.id = :groupId")
    Optional<GroupEntity> findByIdForUpdate(@Param("groupId") UUID groupId);
}

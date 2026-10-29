package com.devstreak.repositories;

import com.devstreak.entities.GroupInviteEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupInviteRepository extends JpaRepository<GroupInviteEntity, UUID> {

    Optional<GroupInviteEntity> findByTokenHash(String tokenHash);

    //---- Row lock makes usage accounting atomic when two users accept the same invite concurrently.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM GroupInviteEntity i WHERE i.tokenHash = :tokenHash")
    Optional<GroupInviteEntity> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    List<GroupInviteEntity> findAllByGroupIdOrderByCreatedAtDesc(UUID groupId);

    Optional<GroupInviteEntity> findByIdAndGroupId(UUID id, UUID groupId);
}

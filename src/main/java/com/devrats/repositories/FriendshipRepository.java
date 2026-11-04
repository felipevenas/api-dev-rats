package com.devrats.repositories;

import com.devrats.entities.FriendshipEntity;
import com.devrats.enums.FriendshipStatus;
import com.devrats.enums.UserStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

/**
 * Consultas do grafo social.
 *
 * <p>Regras de segurança aplicadas aqui:</p>
 * <ul>
 *   <li>autorização sempre no {@code WHERE} (por {@code requester_id}/{@code addressee_id});</li>
 *   <li>locks pessimistas apenas na linha do par (não há linha para travar quando
 *       a solicitação ainda não existe — nesse caso o índice único decide);</li>
 *   <li>listagens filtram usuários {@code ACTIVE} dos dois lados.</li>
 * </ul>
 */
public interface FriendshipRepository extends JpaRepository<FriendshipEntity, UUID> {

    String PAIR_WHERE = "((f.requester.id = :first and f.addressee.id = :second)"
            + " or (f.requester.id = :second and f.addressee.id = :first))";

    /**
     * Trava a linha do par para impedir corrida entre solicitações cruzadas.
     * Quando o par ainda não existe nada é travado; o índice único cobre esse caso.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FriendshipEntity f where " + PAIR_WHERE)
    Optional<FriendshipEntity> findPairForUpdate(@Param("first") UUID first, @Param("second") UUID second);

    @Query("select f from FriendshipEntity f where " + PAIR_WHERE)
    Optional<FriendshipEntity> findPair(@Param("first") UUID first, @Param("second") UUID second);

    Optional<FriendshipEntity> findByIdAndAddresseeId(UUID id, UUID addresseeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<FriendshipEntity> findByIdAndAddresseeIdAndStatus(UUID id, UUID addresseeId, FriendshipStatus status);

    Optional<FriendshipEntity> findByIdAndRequesterId(UUID id, UUID requesterId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<FriendshipEntity> findByIdAndRequesterIdAndStatus(UUID id, UUID requesterId, FriendshipStatus status);

    /**
     * Amigos (ACEITO) com os dois usuários carregados para montar o DTO dentro da transação.
     */
    @Query(value = """
            select f from FriendshipEntity f
            join fetch f.requester
            join fetch f.addressee
            where f.status = :status
              and (f.requester.id = :userId or f.addressee.id = :userId)
              and f.requester.status = :activeStatus
              and f.addressee.status = :activeStatus
            order by f.respondedAt desc, f.createdAt desc
            """, countQuery = """
            select count(f) from FriendshipEntity f
            where f.status = :status
              and (f.requester.id = :userId or f.addressee.id = :userId)
              and f.requester.status = :activeStatus
              and f.addressee.status = :activeStatus
            """)
    Page<FriendshipEntity> findFriends(@Param("userId") UUID userId,
                                       @Param("status") FriendshipStatus status,
                                       @Param("activeStatus") com.devrats.enums.UserStatus activeStatus,
                                       Pageable pageable);

    /**
     * Solicitações recebidas; apenas {@code PENDING} chega ao cliente.
     */
    @Query(value = """
            select f from FriendshipEntity f
            join fetch f.requester
            join fetch f.addressee
            where f.addressee.id = :userId
              and f.status = :status
              and f.requester.status = :activeStatus
              and f.addressee.status = :activeStatus
            order by f.createdAt desc
            """, countQuery = """
            select count(f) from FriendshipEntity f
            where f.addressee.id = :userId
              and f.status = :status
              and f.requester.status = :activeStatus
              and f.addressee.status = :activeStatus
            """)
    Page<FriendshipEntity> findReceivedRequests(@Param("userId") UUID userId,
                                                @Param("status") FriendshipStatus status,
                                                @Param("activeStatus") com.devrats.enums.UserStatus activeStatus,
                                                Pageable pageable);

    /**
     * Solicitações enviadas; apenas {@code PENDING} chega ao cliente.
     */
    @Query(value = """
            select f from FriendshipEntity f
            join fetch f.requester
            join fetch f.addressee
            where f.requester.id = :userId
              and f.status = :status
              and f.requester.status = :activeStatus
              and f.addressee.status = :activeStatus
            order by f.createdAt desc
            """, countQuery = """
            select count(f) from FriendshipEntity f
            where f.requester.id = :userId
              and f.status = :status
              and f.requester.status = :activeStatus
              and f.addressee.status = :activeStatus
            """)
    Page<FriendshipEntity> findSentRequests(@Param("userId") UUID userId,
                                            @Param("status") FriendshipStatus status,
                                            @Param("activeStatus") com.devrats.enums.UserStatus activeStatus,
                                            Pageable pageable);

    /**
     * Busca em lote do status entre o usuário e até 50 contrapartes
     * (somente estados que importam publicamente).
     */
    @Query("""
            select f from FriendshipEntity f
            where f.status in :statuses
              and ((f.requester.id = :userId and f.addressee.id in :otherIds)
                or (f.addressee.id = :userId and f.requester.id in :otherIds))
              and f.requester.status = :activeStatus
              and f.addressee.status = :activeStatus
            """)
    List<FriendshipEntity> findPairsWithStatuses(@Param("userId") UUID userId,
                                                 @Param("otherIds") Collection<UUID> otherIds,
                                                 @Param("statuses") Collection<FriendshipStatus> statuses,
                                                 @Param("activeStatus") UserStatus activeStatus);

    @Query("""
            select count(f) from FriendshipEntity f
            where f.status = :status
              and (f.requester.id = :userId or f.addressee.id = :userId)
              and f.requester.status = :activeStatus
              and f.addressee.status = :activeStatus
            """)
    long countFriends(@Param("userId") UUID userId,
                      @Param("status") FriendshipStatus status,
                      @Param("activeStatus") UserStatus activeStatus);

    long countByRequesterIdAndStatus(UUID requesterId, FriendshipStatus status);

    List<FriendshipEntity> findAllByRequesterIdOrAddresseeId(UUID requesterId, UUID addresseeId);

    /**
     * Remove o grafo social do titular. O delete de conta é soft delete,
     * então o {@code ON DELETE CASCADE} do banco não é disparado.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from FriendshipEntity f where f.requester.id = :userId or f.addressee.id = :userId")
    int deleteAllForUser(@Param("userId") UUID userId);

    /** Remove solicitações recusadas/canceladas antigas (retenção LGPD). */
    @Modifying(flushAutomatically = true)
    @Query("delete from FriendshipEntity f where f.status in :statuses and f.updatedAt < :cutoff")
    int deleteStaleRequests(@Param("statuses") Collection<FriendshipStatus> statuses,
                            @Param("cutoff") Instant cutoff);
}

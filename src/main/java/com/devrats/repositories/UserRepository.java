package com.devrats.repositories;

import com.devrats.entities.UserEntity;
import com.devrats.enums.UserStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByEmailIgnoreCase(String email);

    Optional<UserEntity> findByUsernameIgnoreCase(String username);

    Optional<UserEntity> findByUsername(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByIdAndStatus(UUID id, UserStatus status);

    Optional<UserEntity> findByUsernameAndStatus(String username, UserStatus status);

    /**
     * Busca por prefixo de username, apenas contas {@code ACTIVE} e nunca o próprio usuário.
     *
     * <p>{@code pattern} já vem escapado pelo serviço ({@code \}, {@code %} e {@code _}),
     * e o {@code ESCAPE '\'} garante que curingas não virem busca por trecho.</p>
     */
    @Query("""
            select u from UserEntity u
            where u.status = :status
              and u.id <> :excludeUserId
              and u.username like :pattern escape '\\'
            order by u.username
            """)
    List<UserEntity> searchByUsernamePrefix(@Param("status") UserStatus status,
                                            @Param("pattern") String pattern,
                                            @Param("excludeUserId") UUID excludeUserId,
                                            Pageable pageable);
}

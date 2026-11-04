package com.devrats.services.impl;

import com.devrats.dto.FriendshipStatusResponse;
import com.devrats.dto.PublicUserResponse;
import com.devrats.dto.UserSearchResult;
import com.devrats.entities.UserEntity;
import com.devrats.enums.FriendshipStatus;
import com.devrats.enums.FriendshipStatusView;
import com.devrats.enums.PresenceStatus;
import com.devrats.enums.TechStack;
import com.devrats.enums.UserStatus;
import com.devrats.exceptions.ErrorCode;
import com.devrats.exceptions.NotFoundException;
import com.devrats.exceptions.ValidationException;
import com.devrats.repositories.FriendshipRepository;
import com.devrats.repositories.UserRepository;
import com.devrats.services.FriendService;
import com.devrats.services.PresenceService;
import com.devrats.services.SocialRateLimitService;
import com.devrats.services.UserDirectoryService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Busca e perfil público.
 *
 * <p>Regras de segurança:</p>
 * <ul>
 *   <li>somente contas {@code ACTIVE}, nunca o próprio usuário na busca;</li>
 *   <li>prefixo puro com {@code LIKE ... ESCAPE '\'} — sem {@code contains};</li>
 *   <li>perfil inexistente e conta excluída devolvem o mesmo 404;</li>
 *   <li>presença apenas para {@code FRIENDS}/{@code SELF}.</li>
 * </ul>
 */
@Service
public class UserDirectoryServiceImpl implements UserDirectoryService {

    private static final Pattern QUERY_PATTERN = Pattern.compile("^[a-z0-9_]{2,30}$");
    private static final int MAX_LIMIT = 10;

    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final FriendService friendService;
    private final PresenceService presenceService;
    private final SocialRateLimitService socialRateLimitService;

    public UserDirectoryServiceImpl(UserRepository userRepository,
                                    FriendshipRepository friendshipRepository,
                                    FriendService friendService,
                                    PresenceService presenceService,
                                    SocialRateLimitService socialRateLimitService) {
        this.userRepository = userRepository;
        this.friendshipRepository = friendshipRepository;
        this.friendService = friendService;
        this.presenceService = presenceService;
        this.socialRateLimitService = socialRateLimitService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserSearchResult> search(UUID currentUserId, String query, int limit) {
        socialRateLimitService.checkSearch(currentUserId);
        String normalized = normalizeQuery(query);
        int boundedLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
        List<UserEntity> users = userRepository.searchByUsernamePrefix(UserStatus.ACTIVE,
                escapeLike(normalized) + "%", currentUserId, PageRequest.of(0, boundedLimit));
        if (users.isEmpty()) {
            return List.of();
        }
        Map<UUID, FriendshipStatusView> statuses = friendService
                .statuses(currentUserId, users.stream().map(UserEntity::getId).toList()).stream()
                .collect(Collectors.toMap(FriendshipStatusResponse::userId, FriendshipStatusResponse::status));
        return users.stream()
                .map(user -> new UserSearchResult(user.getId(), user.getUsername(), user.getDisplayName(),
                        user.getAvatarUrl(), user.getExperienceLevel(), TechStack.fromCsv(user.getPrimaryStack()),
                        statuses.getOrDefault(user.getId(), FriendshipStatusView.NONE)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PublicUserResponse getPublicProfile(UUID currentUserId, String username) {
        socialRateLimitService.checkProfile(currentUserId);
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        if (!QUERY_PATTERN.matcher(normalized).matches()) {
            //---- 404 uniforme: não confirma se o username existe ou já foi excluído.
            throw new NotFoundException(ErrorCode.USER_NOT_FOUND);
        }
        UserEntity user = userRepository.findByUsernameAndStatus(normalized, UserStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND));
        FriendshipStatusView friendshipStatus = friendService
                .statuses(currentUserId, List.of(user.getId())).stream()
                .findFirst()
                .map(FriendshipStatusResponse::status)
                .orElse(FriendshipStatusView.NONE);
        PresenceStatus presence = friendshipStatus == FriendshipStatusView.FRIENDS
                || friendshipStatus == FriendshipStatusView.SELF
                ? presenceService.statusOf(user.getId())
                : null;
        long friendsCount = friendshipRepository.countFriends(user.getId(), FriendshipStatus.ACCEPTED,
                UserStatus.ACTIVE);
        return new PublicUserResponse(user.getId(), user.getUsername(), user.getDisplayName(),
                user.getAvatarUrl(), user.getBio(), user.getLocation(), user.getWebsiteUrl(),
                user.getExperienceLevel(), TechStack.fromCsv(user.getPrimaryStack()), user.getCreatedAt(),
                friendshipStatus, presence, friendsCount);
    }

    private String normalizeQuery(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (!QUERY_PATTERN.matcher(normalized).matches()) {
            throw new ValidationException("A busca deve ter entre 2 e 30 caracteres: letras, números ou _");
        }
        return normalized;
    }

    /**
     * Escapa os curingas do {@code LIKE}. A validação já restringe o alfabeto,
     * mas o escape fica aqui como defesa em profundidade.
     */
    private String escapeLike(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}

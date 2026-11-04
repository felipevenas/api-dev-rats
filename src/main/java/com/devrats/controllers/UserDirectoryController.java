package com.devrats.controllers;

import com.devrats.dto.PublicUserResponse;
import com.devrats.dto.UserSearchResult;
import com.devrats.services.UserDirectoryService;
import com.devrats.utils.AuthenticatedUserHelper;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Diretório público: busca por username e perfil público.
 *
 * <p>Todas as respostas são privadas e marcadas com {@code Cache-Control: no-store}
 * pelo filtro de cache.</p>
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserDirectoryController {

    private final UserDirectoryService userDirectoryService;

    public UserDirectoryController(UserDirectoryService userDirectoryService) {
        this.userDirectoryService = userDirectoryService;
    }

    @GetMapping("/search")
    public ResponseEntity<List<UserSearchResult>> search(@AuthenticationPrincipal Jwt jwt,
                                                         @RequestParam("q") String query,
                                                         @RequestParam(defaultValue = "10") int limit) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        return ResponseEntity.ok(userDirectoryService.search(userId, query, Math.min(limit, 10)));
    }

    @GetMapping("/{username}")
    public ResponseEntity<PublicUserResponse> getPublicProfile(@AuthenticationPrincipal Jwt jwt,
                                                               @PathVariable String username) {
        UUID userId = AuthenticatedUserHelper.getUserId(jwt);
        return ResponseEntity.ok(userDirectoryService.getPublicProfile(userId, username));
    }
}

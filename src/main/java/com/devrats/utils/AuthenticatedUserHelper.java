package com.devrats.utils;

import com.devrats.exceptions.ErrorCode;
import com.devrats.exceptions.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

public final class AuthenticatedUserHelper {

    private AuthenticatedUserHelper() {
    }

    public static UUID getUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new UnauthorizedException();
        }
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException e) {
            throw new UnauthorizedException(ErrorCode.UNAUTHORIZED, "Token sem usuário válido");
        }
    }

    public static UUID getUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new UnauthorizedException();
        }
        return getUserId(jwt);
    }
}

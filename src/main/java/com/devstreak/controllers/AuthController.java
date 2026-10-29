package com.devstreak.controllers;

import com.devstreak.config.AppProperties;
import com.devstreak.config.JwtProperties;
import com.devstreak.dto.AuthResponse;
import com.devstreak.dto.LoginRequest;
import com.devstreak.dto.PasswordResetConfirm;
import com.devstreak.dto.PasswordResetRequest;
import com.devstreak.dto.PasswordResetResponse;
import com.devstreak.dto.RegisterRequest;
import com.devstreak.exceptions.ForbiddenException;
import com.devstreak.exceptions.UnauthorizedException;
import com.devstreak.services.AuthService;
import com.devstreak.services.AuthSession;
import com.devstreak.utils.OriginHelper;
import com.devstreak.utils.RefreshCookieHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtProperties jwtProperties;
    private final AppProperties appProperties;

    public AuthController(AuthService authService, JwtProperties jwtProperties, AppProperties appProperties) {
        this.authService = authService;
        this.jwtProperties = jwtProperties;
        this.appProperties = appProperties;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthSession session = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, cookie(session.refreshToken()).toString())
                .body(session.response());
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthSession session = authService.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie(session.refreshToken()).toString())
                .body(session.response());
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = RefreshCookieHelper.COOKIE_NAME, required = false) String refreshToken,
            HttpServletRequest request) {
        requireTrustedOrigin(request);
        try {
            AuthSession session = authService.refresh(refreshToken, request.getHeader(HttpHeaders.USER_AGENT));
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookie(session.refreshToken()).toString())
                    .body(session.response());
        } catch (UnauthorizedException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.SET_COOKIE, clearCookie().toString())
                    .build();
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshCookieHelper.COOKIE_NAME, required = false) String refreshToken,
            HttpServletRequest request) {
        requireTrustedOrigin(request);
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearCookie().toString())
                .build();
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<PasswordResetResponse> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request) {
        return ResponseEntity.accepted().body(authService.requestPasswordReset(request.email()));
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirm request) {
        authService.confirmPasswordReset(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    private org.springframework.http.ResponseCookie cookie(String token) {
        return RefreshCookieHelper.create(token, jwtProperties.getRefreshTtl(), appProperties.getCookie().isSecure());
    }

    private org.springframework.http.ResponseCookie clearCookie() {
        return RefreshCookieHelper.clear(appProperties.getCookie().isSecure());
    }

    private void requireTrustedOrigin(HttpServletRequest request) {
        if (!OriginHelper.isAllowed(request.getHeader(HttpHeaders.ORIGIN),
                appProperties.getCors().getAllowedOrigins())) {
            throw new ForbiddenException("Origem não permitida");
        }
    }
}

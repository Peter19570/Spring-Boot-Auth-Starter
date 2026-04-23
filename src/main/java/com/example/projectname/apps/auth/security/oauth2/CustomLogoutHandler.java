package com.example.projectname.apps.auth.security.oauth2;

import com.example.projectname.apps.audit.dto.response.AuditEventResponse;
import com.example.projectname.apps.auth.dto.internal.CustomUserPrincipal;
import com.example.projectname.apps.audit.enums.AuditAction;
import com.example.projectname.apps.auth.repository.token.RefreshTokenRepo;
import com.example.projectname.apps.users.model.User;
import com.example.projectname.apps.users.repository.UserRepo;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.UUID;

/**
 * Universal Logout Handler that revokes refresh tokens for both
 * OAuth2 (Cookie-based) and Standard (Bearer-based) users.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CustomLogoutHandler implements LogoutHandler {

    private final RefreshTokenRepo refreshTokenRepo;
    private final ApplicationEventPublisher eventPublisher;
    private final UserRepo userRepo;

    @Override
    public void logout(HttpServletRequest request,
                       HttpServletResponse response,
                       Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("Logout called with null or unauthenticated authentication");
            clearJwtCookies(response);
            return;
        }

        String refreshToken = null;

        // 1. Try to get token from Cookies (OAuth2/Cookie Flow)
        if (request.getCookies() != null) {
            refreshToken = Arrays.stream(request.getCookies())
                    .filter(cookie -> "refresh_token".equals(cookie.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);
        }

        // 2. If not in cookies, try to get it from a Header
        if (refreshToken == null) {
            refreshToken = request.getHeader("X-Refresh-Token");
        }

        UUID userId = extractUserId(authentication);

        if (userId == null) {
            log.warn("Could not extract user ID from authentication");
            // Still clear cookies
            clearJwtCookies(response);
            return;
        }

        // 3. Revoke from Database (with proper transaction handling)
        if (refreshToken != null && refreshTokenRepo != null) {
            try {
                log.info("Revoking refresh token during logout flow for user: {}", userId);
                refreshTokenRepo.findByTokenHash(refreshToken)
                        .ifPresent(token -> {
                            token.setRevoked(true);
                            refreshTokenRepo.save(token);
                            publishAudit(userId, request);
                        });
            } catch (Exception e) {
                log.error("Failed to revoke refresh token for user: {}", userId, e);
                // Don't rethrow - continue to clear cookies
            }
        }

        // 4. Always clear cookies
        clearJwtCookies(response);

        log.info("Logout completed successfully for user: {}", userId);
    }

    private UUID extractUserId(Authentication authentication) {
        if (authentication == null) {
            return null;
        }

        Object principal = authentication.getPrincipal();

        // Handle different principal types
        if (principal instanceof CustomUserPrincipal customPrincipal) {
            return customPrincipal.user().getId();
        }

        if (principal instanceof UserDetails userDetails) {
            // If you need to map UserDetails to UUID somehow
            String username = userDetails.getUsername();
            return userRepo.findByEmail(username)
                    .map(User::getId)
                    .orElse(null);
        }

        if (principal instanceof String username) {
            return userRepo.findByEmail(username)
                    .map(User::getId)
                    .orElse(null);
        }

        log.warn("Unsupported principal type: {}", principal != null ? principal.getClass() : "null");
        return null;
    }

    private void clearJwtCookies(HttpServletResponse response) {
        String[] cookiesToClear = {"access_token", "refresh_token"};
        for (String cookieName : cookiesToClear) {
            ResponseCookie cookie = ResponseCookie.from(cookieName, "")
                    .httpOnly(true)
                    .secure(true)
                    .path("/")
                    .maxAge(0)
                    .sameSite("Lax")
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        }
    }

    private void publishAudit(UUID userId, HttpServletRequest request) {
        // You need to inject eventPublisher as a field
        eventPublisher.publishEvent(new AuditEventResponse(
                userId,
                AuditAction.LOGOUT,
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                null
        ));
    }
}
package com.example.projectname.microservice.authentication.security.oauth2;

import com.example.projectname.microservice.authentication.dto.internal.AuditEventResponse;
import com.example.projectname.microservice.authentication.enums.AuditAction;
import com.example.projectname.microservice.authentication.repo.RefreshTokenRepo;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
    private final HttpServletRequest request;

    @Override
    @Transactional
    public void logout(HttpServletRequest request,
                       HttpServletResponse response,
                       Authentication authentication) {

        String refreshToken = null;

        // 1. Try to get token from Cookies (OAuth2/Cookie Flow)
        if (request.getCookies() != null) {
            refreshToken = Arrays.stream(request.getCookies())
                    .filter(cookie -> "refresh_token".equals(cookie.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);
        }

        // 2. If not in cookies, try to get it from a Header (Standard JSON Flow)
        // Note: Usually, for Bearer logout, the client sends the REFRESH token
        // in a custom header like 'X-Refresh-Token' or in the body.
        if (refreshToken == null) {
            refreshToken = request.getHeader("X-Refresh-Token");
        }

        // 3. Revoke from Database
        if (refreshToken != null) {
            log.info("Revoking refresh token during logout flow");
            refreshTokenRepo.findByTokenHash(refreshToken)
                    .ifPresent(token -> {
                        // Hard delete or Soft revoke
                        token.setRevoked(true);
                    });
        }

        // 4. Always attempt to clear cookies just in case
        clearJwtCookies(response);
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

    private void publishAudit(UUID userId, AuditAction action, String metadata) {
        eventPublisher.publishEvent(new AuditEventResponse(
                userId,
                action,
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                metadata
        ));
    }
}
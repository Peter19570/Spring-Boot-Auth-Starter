package com.example.projectname.microservice.authentication.security.oauth2;

import com.example.projectname.microservice.authentication.dto.internal.AuditEventResponse;
import com.example.projectname.microservice.authentication.dto.internal.CustomUserPrincipal;
import com.example.projectname.microservice.authentication.enums.AuditAction;
import com.example.projectname.microservice.authentication.model.token.RefreshToken;
import com.example.projectname.microservice.authentication.repo.token.RefreshTokenRepo;
import com.example.projectname.microservice.authentication.security.jwt.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

/**
 * Handler executed after successful OAuth2 authentication.
 * It generates JWT tokens and attaches them as HttpOnly cookies before redirecting to the frontend.
 */
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final RefreshTokenRepo refreshTokenRepo;
    private final ApplicationEventPublisher eventPublisher;
    private final HttpServletRequest request;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        CustomUserPrincipal principal = (CustomUserPrincipal) authentication.getPrincipal();

        // 1. Generate our internal system tokens
        assert principal != null;
        String accessToken = jwtService.generateAccessToken(principal);
        String refreshToken = jwtService.generateRefreshToken(principal);

        // Save the refresh token to the db
        RefreshToken token = new RefreshToken();
        token.setTokenHash(refreshToken);
        token.setUser(principal.user());
        token.setExpiresAt(Instant.now().plusSeconds(60 * 60 * 24 * 7));
        refreshTokenRepo.save(token);

        // 2. Attach tokens to HttpOnly, Secure Cookies
        addCookie(response, "access_token", accessToken,
                jwtService.getAccessExpirationInSeconds(), "/");

        addCookie(response, "refresh_token", refreshToken,
                jwtService.getRefreshExpirationInSeconds(), "/api/v1/auth/refresh");

        // 3. Redirect back to React dashboard
        getRedirectStrategy().sendRedirect(request, response, frontendUrl + "/dashboard");
        publishAudit(principal.user().getId(), AuditAction.LOGIN_SUCCESS, null);
    }

    /**
     * Creates and adds a secure, HttpOnly, SameSite-compliant cookie to the response.
     *
     * @param response       The HttpServletResponse to attach the cookie to.
     * @param name           The name of the cookie (e.g., access_token).
     * @param value          The JWT string.
     * @param maxAgeInSeconds The duration the cookie remains valid.
     */
    private void addCookie(HttpServletResponse response, String name,
                           String value, long maxAgeInSeconds, String tokenPath) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(true)
                .path(tokenPath)
                .maxAge(maxAgeInSeconds)
                .sameSite("Lax")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
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

package com.example.projectname.apps.auth.security.jwt;

import com.example.projectname.apps.auth.dto.internal.CustomUserPrincipal;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // 1. Extract the token using our new hybrid method
        final String jwt = getTokenFromRequest(request);
        final String username;

        // 2. If no token is found in Header OR Cookie, move to the next filter
        if (jwt == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            username = jwtService.extractUsername(jwt);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                // Since we are using our "Overpowered" principal, we cast it here
                CustomUserPrincipal principal = (CustomUserPrincipal) this.userDetailsService
                        .loadUserByUsername(username);

                if (jwtService.isTokenValid(jwt, principal)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            principal.getAuthorities()
                    );

                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
            filterChain.doFilter(request, response);

        } catch (ExpiredJwtException e) {
            handleException(response, "Token has expired");
        } catch (MalformedJwtException e) {
            handleException(response, "Invalid token format");
        }
    }

    /**
     * Smart retrieval method that checks both Authorization header and cookies for the access token
     * */
    private String getTokenFromRequest(HttpServletRequest request) {
        // Option A: Check Authorization Header (Standard/Mobile)
        final String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        // Option B: Check Cookies (Social/OAuth2 Handover)
        if (request.getCookies() != null) {
            return Arrays.stream(request.getCookies())
                    .filter(cookie -> "accessToken".equals(cookie.getName()))
                    .findFirst()
                    .map(jakarta.servlet.http.Cookie::getValue)
                    .orElse(null);
        }

        return null;
    }

    /**
     * Format token error message
     * */
    private void handleException(HttpServletResponse response, String message)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        String jsonResponse = String.format("{\"msg\": \"%s\"}", message);
        response.getWriter().write(jsonResponse);
    }
}
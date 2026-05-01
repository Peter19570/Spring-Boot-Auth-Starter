package com.example.projectname.apps.auth.controller;

import com.example.projectname.apps.auth.dto.internal.ApiResponse;
import com.example.projectname.apps.auth.dto.internal.CustomUserPrincipal;
import com.example.projectname.apps.auth.dto.request.*;
import com.example.projectname.apps.auth.dto.response.AuthResponse;
import com.example.projectname.apps.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for authentication lifecycle.
 * Handled endpoints: Registration, Login, Token Refresh, and Logout.
 */
@RestController
@Slf4j
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.ok(new ApiResponse<>(
                "User registered successfully", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request
    ) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(new ApiResponse<>(
                "Login successful", response));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        AuthResponse response = authService.refresh(request);
        return ResponseEntity.ok(new ApiResponse<>(
                "Token refreshed successfully", response));
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> logout(
            @Valid @RequestBody RefreshTokenRequest request,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        authService.logout(request.refreshToken(), principal.user());
        return ResponseEntity.ok(new ApiResponse<>(
                "Logout successful", null));
    }

    /**
     * React hits this when the baseUser arrives at /verify-email?token=...
     */
    @GetMapping("/verify-email")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(
            @RequestParam String token) {
        log.info("Email verification attempt with token: {}", token);
        authService.verifyEmail(token);
        return ResponseEntity.ok(new ApiResponse<>(
                "Email verified successfully!", null));
    }

    /**
     * React hits this when baseUser submits their email on the "Forgot Password" page.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        authService.requestPasswordReset(request);
        // Always return 200 OK to prevent account enumeration
        return ResponseEntity.ok(new ApiResponse<>(
                "If an account exists, a reset link has been sent.", null));
    }

    /**
     * React hits this when baseUser submits the "New Password" form.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        log.info("Processing password reset for token.");
        authService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok(new ApiResponse<>(
                "Password reset successful. Please log in.", null));
    }

    /**
     * Endpoint to request an email change.
     * Access: Authenticated Users Only.
     */
    @PostMapping("/email-change/request")
    @PreAuthorize("isAuthenticated()") // Ensure they are logged in
    public ResponseEntity<ApiResponse<Void>> requestChange(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @RequestBody @Valid EmailChangeRequest request) {

        authService.requestEmailChange(principal.user().getId(), request.newEmail());
        return ResponseEntity.ok(new ApiResponse<>(
                "Email verification link sent", null));
    }

    /**
     * Endpoint to confirm the email change via token.
     * Access: Public (PermitAll).
     */
    @GetMapping("/email-change/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmChange(
            @RequestParam("token") String token) {
        authService.confirmEmailChange(token);
        return ResponseEntity.ok(new ApiResponse<>(
                "Email Update Success", null));
    }
}
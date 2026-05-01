package com.example.projectname.module.auth.service;

import com.example.projectname.module.social.dto.response.SocialResponse;
import com.example.projectname.module.social.model.SocialAccount;
import com.example.projectname.module.auth.service.helper.EmailService;
import com.example.projectname.module.users.mapper.UserMapper;
import com.example.projectname.module.users.model.User;
import com.example.projectname.module.auth.exception.AuthenticationException;
import com.example.projectname.module.audit.dto.response.AuditEventResponse;
import com.example.projectname.module.shared.dto.response.CustomUserPrincipal;
import com.example.projectname.module.auth.dto.request.LoginRequest;
import com.example.projectname.module.auth.dto.request.ForgotPasswordRequest;
import com.example.projectname.module.auth.dto.request.RefreshTokenRequest;
import com.example.projectname.module.auth.dto.request.RegisterRequest;
import com.example.projectname.module.auth.dto.response.AuthResponse;
import com.example.projectname.module.users.dto.response.UserResponse;
import com.example.projectname.module.audit.enums.AuditAction;
import com.example.projectname.module.auth.model.EmailVerificationToken;
import com.example.projectname.module.auth.model.PasswordResetToken;
import com.example.projectname.module.auth.model.RefreshToken;
import com.example.projectname.module.auth.repository.EmailVerificationTokenRepo;
import com.example.projectname.module.auth.repository.PasswordResetTokenRepo;
import com.example.projectname.module.auth.repository.RefreshTokenRepo;
import com.example.projectname.module.users.repository.UserRepo;
import com.example.projectname.module.shared.security.jwt.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service handling production-grade authentication lifecycle:
 * Registration, Login, Token Refresh, and Logout.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    private final UserRepo userRepository;
    private final EmailVerificationTokenRepo emailVerificationTokenRepo;
    private final PasswordResetTokenRepo passwordResetTokenRepo;
    private final RefreshTokenRepo refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final AuthenticationManager authenticationManager;
    private final ApplicationEventPublisher eventPublisher;
    private final HttpServletRequest request;
    private final UserMapper userMapper;

    /**
     * Registers a new user with a hashed password and sends a verification email
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // 1. Check if email exist in the database
        if (userRepository.existsByEmail(request.email())) {
            throw new AuthenticationException("Email already registered");
        }

        User user = new User();
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        // Set role here if I got a role-based system...

        userRepository.save(user);
        log.info("Successfully registered new user: {}", request.email());
        publishAudit(user.getId(), AuditAction.REGISTER);

        // 2. Generate the Link & Token & Save in the database
        String rawToken = UUID.randomUUID().toString();

        EmailVerificationToken verificationToken = new EmailVerificationToken();
        verificationToken.setUser(user);
        verificationToken.setTokenHash(hashToken(rawToken));
        verificationToken.setExpiresAt(Instant.now().plus(Duration.ofDays(1)));
        emailVerificationTokenRepo.save(verificationToken);

        // 3. Hand off to the Async Email Service
        emailService.sendVerificationEmail(user.getEmail(), rawToken);
        log.info("Verification email queued for: {}", user.getEmail());

        return createAuthResponse(user);
    }

    /**
     * Authenticates a baseUser and generates fresh tokens.
     */
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        // 1. PRE-CHECK: Is account locked?
        if (user.isLocked()) {
            if (user.getLockedUntil() != null && user.getLockedUntil().isBefore(Instant.now())) {
                // The lock expired! Reset it.
                user.setLocked(false);
                user.setFailedLoginAttempts(0);
                user.setLockedUntil(null);
                userRepository.save(user);
            } else {
                log.warn("Login blocked: Account {} is currently locked.", user.getEmail());
                publishAudit(user.getId(), AuditAction.LOGIN_FAILURE);
                throw new AuthenticationException("Account is temporarily locked. Try again later.");
            }
        }

        try {
            // 2. PASSWORD CHECK
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );

            // 3. SUCCESS: Reset the counter
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            user.setLocked(false);
            userRepository.save(user);

            publishAudit(user.getId(), AuditAction.LOGIN_SUCCESS);
            return createAuthResponse(user);

        } catch (BadCredentialsException e) {
            // 4. FAILURE: Increment counter
            int newAttempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(newAttempts);

            if (newAttempts >= 5) {
                user.setLocked(true);
                user.setLockedUntil(Instant.now().plus(Duration.ofMinutes(15)));
                log.warn("Account locked: Email {} reached max failed attempts.", user.getEmail());
            }

            publishAudit(user.getId(), AuditAction.LOGIN_FAILURE);
            userRepository.save(user);
            throw e;
        }
    }

    /**
     * Refreshes an access token using a valid refresh token.
     * Implements "Refresh Token Rotation" for high security.
     */
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        String token = request.refreshToken();
        String username = jwtService.extractUsername(token);

        User user = userRepository.findByEmailAndDeletedAtIsNull(username)
                .orElseThrow(() -> {
                    log.error("User, {} with refresh token not found in database", username);
                    return new AuthenticationException("Invalid refresh token");
                });

        // Verify the token exists in DB, isn't revoked, and isn't expired
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(token)
                .filter(t -> !t.isRevoked() && t.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> {
                    log.warn("Potential Token Reuse Attempt! User {} " +
                            "tried to use a revoked/expired refresh token.", username);
                    return new AuthenticationException("Refresh token is invalid or expired");
                });

        // Rotate: Revoke the used token so it can't be used again
        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);
        log.info("Successfully generated token for {}", username);

        return createAuthResponse(user);
    }

    /**
     * Logs the User out by revoking the specific refresh token.
     */
    @Transactional
    public void logout(String refreshToken, User user) {
        publishAudit(user.getId(), AuditAction.LOGOUT);
        refreshTokenRepository.findByTokenHash(refreshToken)
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        // 1. Hash the incoming token to find it in the DB
        String hashedToken = hashToken(rawToken);

        EmailVerificationToken token = emailVerificationTokenRepo.findByTokenHash(hashedToken)
                .filter(t -> !t.isUsed() && t.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new AuthenticationException("Invalid or expired verification token"));

        // 2. Mark token as used and verify the user
        token.setUsed(true);
        User user = token.getUser();
        user.setEmailVerified(true);

        userRepository.save(user);
        emailVerificationTokenRepo.save(token);
        publishAudit(user.getId(), AuditAction.EMAIL_VERIFIED);
    }

    /**
     * Initiates the email change process. Validates that the new email is not taken
     * and sends a verification token to the NEW address.
     */
    @Transactional
    public void requestEmailChange(UUID userId, String newEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationException("User not found"));

        // 1. PROD CHECK: Ensure Social-Only users have a password fallback
        if (user.getPassword() == null) {
            throw new IllegalStateException("Please set a password in settings before changing your email.");
        }

        // 2. Availability Check
        if (userRepository.existsByEmail(newEmail)) {
            throw new IllegalStateException("Email is already in use.");
        }

        // 3. Generate Verification Token
        String token = UUID.randomUUID().toString();
        EmailVerificationToken emailVerificationToken = new EmailVerificationToken();
        emailVerificationToken.setTokenHash(token);
        emailVerificationToken.setNewEmail(newEmail); // Store the pending email in the token record
        emailVerificationToken.setUser(user);
        emailVerificationToken.setExpiresAt(Instant.now().plus(Duration.ofHours(2)));

        emailVerificationTokenRepo.save(emailVerificationToken);

        // 4. Send Email to the NEW address
        emailService.sendEmailChangeConfirmation(newEmail, token);
        log.info("Email change request initiated for user {}: {} -> {}", userId, user.getEmail(), newEmail);
        publishAudit(user.getId(), AuditAction.EMAIL_CHANGE_REQUEST);
    }

    /**
     * Completes the email change process by validating the token and updating the User entity.
     * * @param token The raw token string sent to the user's new email.
     */
    @Transactional
    public void confirmEmailChange(String token) {
        EmailVerificationToken emailVerificationToken = emailVerificationTokenRepo.findByTokenHash(token)
                .orElseThrow(() -> new AuthenticationException("Invalid or expired token"));

        if (emailVerificationToken.getExpiresAt().isBefore(Instant.now())) {
            emailVerificationTokenRepo.delete(emailVerificationToken);
            throw new AuthenticationException("Token has expired");
        }

        User user = emailVerificationToken.getUser();
        String oldEmail = user.getEmail();
        String newEmail = emailVerificationToken.getNewEmail();

        // Update User
        user.setEmail(newEmail);
        userRepository.save(user);

        // Cleanup
        emailVerificationTokenRepo.delete(emailVerificationToken);

        log.info("Email successfully changed for user {}: {} -> {}", user.getId(), oldEmail, newEmail);
        publishAudit(user.getId(), AuditAction.EMAIL_CHANGE_CONFIRM);
    }

    /**
     * Request for password reset. An email is sent when triggered. You cannot reset your password if your account is social-only.
     * */
    @Transactional
    public void requestPasswordReset(ForgotPasswordRequest request) {
        userRepository.findByEmailAndDeletedAtIsNull(request.email()).ifPresentOrElse(user -> {

            // 1. CHECK: Is this a Social-Only user?
            if (user.getPassword() == null && !user.getSocialAccounts().isEmpty()) {
                // Get the first provider (e.g., "GOOGLE")
                String provider = user.getSocialAccounts().get(0).getProvider();

                // PROD STRATEGY: Send a "You use Social Login" email instead of a reset link
                emailService.sendSocialLoginReminder(user.getEmail(), provider);
                log.info("Social login reminder sent for OAuth-only user: {}", user.getId());
                return;
            }

            // 2. Standard Flow: For users who have a password
            String rawToken = UUID.randomUUID().toString();

            PasswordResetToken resetToken = new PasswordResetToken();
            resetToken.setUser(user);
            resetToken.setTokenHash(hashToken(rawToken));
            resetToken.setExpiresAt(Instant.now().plus(Duration.ofMinutes(15)));

            passwordResetTokenRepo.save(resetToken);

            emailService.sendPasswordResetEmail(user.getEmail(), rawToken);
            log.info("Password reset link generated for user: {}", user.getId());

        }, () -> {
            log.warn("Password reset requested for non-existent email: {}", request.email());
        });
    }

    /**
     *  Reset user password
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        // 1. Hash the incoming raw token to match the DB record
        String hashedToken = hashToken(rawToken);

        // 2. Find and validate the token
        PasswordResetToken token = passwordResetTokenRepo.findByTokenHash(hashedToken)
                .filter(t -> !t.isUsed() && t.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> {
                    log.warn("Password reset failed: Invalid or expired token hash {}", hashedToken);
                    return new AuthenticationException("Invalid or expired reset token");
                });

        User user = token.getUser();

        // 3. Update User Credentials & Security State
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setLocked(false);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setEmailVerified(true);

        // This uses the custom query we added to the RefreshTokenRepository
        refreshTokenRepository.revokeAllByUserId(user.getId());
        log.info("All active sessions revoked for user: {}", user.getEmail());

        // 5. Burn the reset token
        token.setUsed(true);

        userRepository.save(user);
        passwordResetTokenRepo.save(token);

        publishAudit(user.getId(), AuditAction.PASSWORD_CHANGE);
        log.info("Password successfully reset for user: {}", user.getEmail());
    }

    /**
     * Internal helper to bundle token generation | DB persistence | Hash Strings
     */
    private AuthResponse createAuthResponse(User user) {
        CustomUserPrincipal principal = new CustomUserPrincipal(user, null);

        String accessToken = jwtService.generateAccessToken(principal);
        String refreshToken = jwtService.generateRefreshToken(principal);

        // Store the new refresh token in DB
        saveRefreshToken(user, refreshToken);

        return new AuthResponse(
                accessToken,
                refreshToken,
                jwtService.extractExpiration(accessToken).getTime() / 1000,
                userMapper.toDto(user)
        );
    }

    /**
     * Saves a new refresh token for the given user.
     */
    private void saveRefreshToken(User user, String token) {
        RefreshToken rt = new RefreshToken();
        rt.setUser(user);
        rt.setTokenHash(token); // Ideally, further hash this value before storing
        rt.setExpiresAt(Instant.now().plusSeconds(60 * 60 * 24 * 7)); // 7 days
        refreshTokenRepository.save(rt);
    }

    /**
     * Hashes the given raw token using SHA-256 algorithm and returns the result
     * as a lowercase hexadecimal string.
     */
    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));

            // Classic hex conversion
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error hashing token", e);
        }
    }

    private void publishAudit(UUID userId, AuditAction action) {
        eventPublisher.publishEvent(new AuditEventResponse(
                userId,
                action,
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                null
        ));
    }
}



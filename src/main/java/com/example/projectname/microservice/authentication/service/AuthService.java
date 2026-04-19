package com.example.projectname.microservice.authentication.service;

import com.example.projectname.microservice.authentication.dto.internal.AuditEventResponse;
import com.example.projectname.microservice.authentication.dto.internal.CustomUserPrincipal;
import com.example.projectname.microservice.authentication.dto.request.LoginRequest;
import com.example.projectname.microservice.authentication.dto.request.ForgotPasswordRequest;
import com.example.projectname.microservice.authentication.dto.request.RefreshTokenRequest;
import com.example.projectname.microservice.authentication.dto.request.RegisterRequest;
import com.example.projectname.microservice.authentication.dto.response.AuthResponse;
import com.example.projectname.microservice.authentication.dto.response.SocialAccountResponse;
import com.example.projectname.microservice.authentication.dto.response.UserResponse;
import com.example.projectname.microservice.authentication.enums.AuditAction;
import com.example.projectname.microservice.authentication.exception.InvalidTokenException;
import com.example.projectname.microservice.authentication.exception.LockedException;
import com.example.projectname.microservice.authentication.exception.ResourceNotFoundException;
import com.example.projectname.microservice.authentication.model.*;
//import com.example.projectname.microservice.authentication.*;
import com.example.projectname.microservice.authentication.repo.EmailVerificationTokenRepo;
import com.example.projectname.microservice.authentication.repo.PasswordResetTokenRepo;
import com.example.projectname.microservice.authentication.repo.RefreshTokenRepo;
import com.example.projectname.microservice.authentication.repo.UserRepo;
import com.example.projectname.microservice.authentication.security.jwt.JwtService;
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

    /**
     * Registers a new baseUser with a hashed password and sends a verification email
     * @param request The registration details
     * @return AuthResponse containing the initial set of tokens
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new RuntimeException("Email already registered");
        }

        User user = new User();
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        // Note: role defaults to USER in your entity definition

        userRepository.save(user);
        log.info("Successfully registered new baseUser: {}", request.email());

        // 2. Generate the Link & Token & Save in the database
        String rawToken = UUID.randomUUID().toString();

        EmailVerificationToken verificationToken = new EmailVerificationToken();
        verificationToken.setUser(user); // [cite: 24]
        verificationToken.setTokenHash(hashToken(rawToken)); // [cite: 25]
        verificationToken.setExpiresAt(Instant.now().plus(Duration.ofDays(1))); // [cite: 26]

        emailVerificationTokenRepo.save(verificationToken);

        // 3. Hand off to the Async Email Service
        emailService.sendVerificationEmail(user.getEmail(), rawToken);
        log.info("Verification email queued for: {}", user.getEmail());

        return createAuthResponse(user);
    }

    /**
     * Authenticates a baseUser and generates fresh tokens.
     * @param request Email and Password credentials
     * @return AuthResponse containing access and refresh tokens
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
                publishAudit(user.getId(), AuditAction.LOGIN_FAILURE, null);
                throw new LockedException("Account is temporarily locked. Try again later.");
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


            publishAudit(user.getId(), AuditAction.LOGIN_SUCCESS, null);
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

            publishAudit(user.getId(), AuditAction.LOGIN_FAILURE, null);
            userRepository.save(user);
            throw e;
        }
    }

    /**
     * Refreshes an access token using a valid refresh token.
     * Implements "Refresh Token Rotation" for high security.
     * @param request The plaintext refresh token from the client
     * @return A new pair of access and refresh tokens
     */
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        String token = request.refreshToken();
        String username = jwtService.extractUsername(token);

        User user = userRepository.findByEmailAndDeletedAtIsNull(username)
                .orElseThrow(() -> {
                    log.error("User, {} with refresh token not found in database", username);
                    return new RuntimeException("Invalid refresh token");
                });

        // Verify the token exists in DB, isn't revoked, and isn't expired
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(token)
                .filter(t -> !t.isRevoked() && t.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> {
                    log.warn("Potential Token Reuse Attempt! User {} " +
                            "tried to use a revoked/expired refresh token.", username);
                    return new InvalidTokenException("Refresh token is invalid or expired");
                });

        // Rotate: Revoke the used token so it can't be used again
        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);
        log.info("Successfully generated token for {}", username);

        return createAuthResponse(user);
    }

    /**
     * Logs the baseUser out by revoking the specific refresh token.
     * @param refreshToken The token to invalidate
     */
    @Transactional
    public void logout(String refreshToken, User user) {
        publishAudit(user.getId(), AuditAction.LOGOUT, null);
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
                .orElseThrow(() -> new InvalidTokenException("Invalid or expired verification token"));

        // 2. Mark token as used and verify the baseUser
        token.setUsed(true); // [cite: 26]
        User user = token.getUser();
        user.setEmailVerified(true); //

        userRepository.save(user);
        publishAudit(user.getId(), AuditAction.EMAIL_VERIFIED, null);
        emailVerificationTokenRepo.save(token);
    }

    /**
     * Initiates the email change process. Validates that the new email is not taken
     * and sends a verification token to the NEW address.
     * * @param userId The ID of the currently authenticated user.
     * @param newEmail The target email address the user wishes to switch to.
     * @throws IllegalStateException If the user is Social-Only or email is taken.
     */
    @Transactional
    public void requestEmailChange(UUID userId, String newEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

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
        publishAudit(user.getId(), AuditAction.EMAIL_CHANGE_REQUEST, null);
    }

    /**
     * Completes the email change process by validating the token and updating the User entity.
     * * @param token The raw token string sent to the user's new email.
     */
    @Transactional
    public void confirmEmailChange(String token) {
        EmailVerificationToken emailVerificationToken = emailVerificationTokenRepo.findByTokenHash(token)
                .orElseThrow(() -> new InvalidTokenException("Invalid or expired token"));

        if (emailVerificationToken.getExpiresAt().isBefore(Instant.now())) {
            emailVerificationTokenRepo.delete(emailVerificationToken);
            throw new InvalidTokenException("Token has expired");
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
        publishAudit(user.getId(), AuditAction.EMAIL_CHANGE_CONFIRM, null);
    }

    @Transactional
    public void requestPasswordReset(ForgotPasswordRequest request) {
        // 1. Account Enumeration Protection: Same response for existing/non-existing emails
        userRepository.findByEmailAndDeletedAtIsNull(request.email()).ifPresentOrElse(user -> {

            // 2. CHECK: Is this a Social-Only user?
            if (user.getPassword() == null && !user.getSocialAccounts().isEmpty()) {
                // Get the first provider (e.g., "GOOGLE")
                String provider = user.getSocialAccounts().get(0).getProvider();

                // PROD STRATEGY: Send a "You use Social Login" email instead of a reset link
                emailService.sendSocialLoginReminder(user.getEmail(), provider);
                log.info("Social login reminder sent for OAuth-only user: {}", user.getId());
                return;
            }

            // 3. Standard Flow: For users who have a password
            String rawToken = UUID.randomUUID().toString();

            PasswordResetToken resetToken = new PasswordResetToken();
            resetToken.setUser(user);
            resetToken.setTokenHash(hashToken(rawToken));
            resetToken.setExpiresAt(Instant.now().plus(Duration.ofMinutes(15)));

            passwordResetTokenRepo.save(resetToken);

            emailService.sendPasswordResetEmail(user.getEmail(), rawToken);
            log.info("Password reset link generated for user: {}", user.getId());

        }, () -> {
            // Log for monitoring, but API remains silent
            log.warn("Password reset requested for non-existent email: {}", request.email());
        });
    }

    /**
     * Finalizes the password reset process.
     * Invalidates all current sessions to ensure account security.
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
                    return new RuntimeException("Invalid or expired reset token");
                });

        User user = token.getUser();

        // 3. Update User Credentials & Security State
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setLocked(false);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        // If they can reset via email, they have effectively verified their email
        user.setEmailVerified(true);

        // 4. THE NUCLEAR OPTION: Revoke all refresh tokens
        // This uses the custom query we added to the RefreshTokenRepository
        refreshTokenRepository.revokeAllByUserId(user.getId());
        log.info("All active sessions revoked for baseUser: {}", user.getEmail());

        // 5. Burn the reset token
        token.setUsed(true);

        userRepository.save(user);
        passwordResetTokenRepo.save(token);

        publishAudit(user.getId(), AuditAction.PASSWORD_CHANGE, null);
        log.info("Password successfully reset for baseUser: {}", user.getEmail());
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
                jwtService.extractExpiration(accessToken).getTime() / 1000, // Seconds
                mapToUserResponse(user)
        );
    }

    /**
     * Saves a new refresh token for the given user.
     *
     * @param user  the user to associate the refresh token with
     * @param token the refresh token to store (should be hashed before calling this method)
     */
    private void saveRefreshToken(User user, String token) {
        RefreshToken rt = new RefreshToken();
        rt.setUser(user);
        rt.setTokenHash(token); // Ideally, further hash this value before storing
        rt.setExpiresAt(Instant.now().plusSeconds(60 * 60 * 24 * 7)); // 7 days
        refreshTokenRepository.save(rt);
    }

    /**
     * Maps a User entity to a UserResponse DTO.
     * Handles the collection of social accounts safely.
     */
    private UserResponse mapToUserResponse(User user) {
        List<SocialAccountResponse> socialResponses = Optional.ofNullable(user.getSocialAccounts())
                .orElse(Collections.emptyList())
                .stream()
                .map(this::mapToSocialResponse)
                .toList();

        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole().name(),
                user.getAvatarUrl(),
                user.isEmailVerified(),
                socialResponses
        );
    }

    /**
     * Maps a single SocialAccount entity to a SocialAccountResponse DTO.
     */
    private SocialAccountResponse mapToSocialResponse(SocialAccount socialAccount) {
        if (socialAccount == null) return null;
        return new SocialAccountResponse(
                socialAccount.getProvider()
        );
    }

    /**
     * Hashes the given raw token using SHA-256 algorithm and returns the result
     * as a lowercase hexadecimal string.
     *
     * @param rawToken the original token string to be hashed
     * @return the SHA-256 hash of the input as a 64-character lowercase hex string
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



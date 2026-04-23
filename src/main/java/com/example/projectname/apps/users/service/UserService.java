package com.example.projectname.apps.user.service;

import com.example.projectname.apps.auth.service.helper.EmailService;
import com.example.projectname.apps.auth.service.helper.InMemoryOtpService;
import com.example.projectname.apps.user.model.User;
import com.example.projectname.apps.user.repository.UserRepo;
import com.example.projectname.exception.custom.AuthenticationException;
import com.example.projectname.apps.audit.dto.response.AuditEventResponse;
import com.example.projectname.apps.audit.enums.AuditAction;
import com.example.projectname.apps.auth.repository.token.RefreshTokenRepo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Handles the multi-step secure deletion of a user account.
 * Uses a soft-delete approach to allow for data recovery/auditing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepo userRepository;
    private final RefreshTokenRepo refreshTokenRepo;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final InMemoryOtpService otpService;
    private final ApplicationEventPublisher eventPublisher;
    private final HttpServletRequest request;

    /**
     * Initiates deletion by sending a verification code.
     */
    public void initiateDeletion(User user) {
        String code = otpService.generateOtp(user.getEmail());
        emailService.sendAccountDeletionCode(user.getEmail(), code);
        log.info("Deletion OTP sent to user: {}", user.getEmail());
    }

    /**
     * Performs the soft delete after validating password and OTP.
     */
    @Transactional
    public void confirmSoftDelete(UUID userId, String password, String otp) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationException("User not found"));

        // 1. Password Challenge (Only if they have one)
        if (user.getPassword() != null) {
            if (password == null || !passwordEncoder.matches(password, user.getPassword())) {
                throw new BadCredentialsException("Invalid password provided for account deletion.");
            }
        }

        // 2. OTP Challenge
        if (!otpService.validateOtp(user.getEmail(), otp.replaceAll("\\s+", ""))) {
            throw new AuthenticationException("Invalid or expired deletion code.");
        }

        // 3. The Soft Delete
        user.setDeletedAt(Instant.now());

        // 4. Revocation: Kick them out of all devices
        refreshTokenRepo.deleteByUser(user);

        userRepository.save(user);
        log.warn("User {} soft-deleted at {}", userId, user.getDeletedAt());
        publishAudit(user.getId());
    }

    private void publishAudit(UUID userId) {
        eventPublisher.publishEvent(new AuditEventResponse(
                userId,
                AuditAction.ACCOUNT_SOFT_DELETE,
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                null
        ));
    }
}

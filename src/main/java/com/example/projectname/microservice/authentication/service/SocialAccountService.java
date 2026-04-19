package com.example.projectname.microservice.authentication.service;

import com.example.projectname.microservice.authentication.dto.internal.AuditEventResponse;
import com.example.projectname.microservice.authentication.enums.AuditAction;
import com.example.projectname.microservice.authentication.exception.ResourceNotFoundException;
import com.example.projectname.microservice.authentication.model.SocialAccount;
import com.example.projectname.microservice.authentication.model.User;
import com.example.projectname.microservice.authentication.repo.SocialAccountRepo;
import com.example.projectname.microservice.authentication.repo.UserRepo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SocialAccountService {

    private final UserRepo userRepository;
    private final SocialAccountRepo socialAccountRepo;
    private final ApplicationEventPublisher eventPublisher;
    private final HttpServletRequest request;

    @Transactional
    public void unlinkProvider(UUID userId, String provider) {
        // 1. Fetch a FRESH user with their social accounts joined
        User user = userRepository.findByIdWithSocialAccounts(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // 2. Find the specific social account to remove
        SocialAccount accountToUnlink = user.getSocialAccounts().stream()
                .filter(sa -> sa.getProvider().equalsIgnoreCase(provider))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Social account link not found"));

        // 3. THE SAFETY RAIL: Check if they are about to lock themselves out
        boolean hasPassword = user.getPassword() != null;
        int socialCount = user.getSocialAccounts().size();

        if (!hasPassword && socialCount <= 1) {
            throw new IllegalStateException(
                    "Cannot unlink your only login method. Please set a password first.");
        }

        // 4. Perform the removal
        user.getSocialAccounts().remove(accountToUnlink);
        socialAccountRepo.delete(accountToUnlink);

        publishAudit(user.getId(), AuditAction.SOCIAL_UNLINK, null);
        log.info("User {} successfully unlinked provider {}", user.getEmail(), provider);
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
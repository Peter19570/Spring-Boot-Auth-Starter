package com.example.projectname.apps.auth.service;

import com.example.projectname.exception.custom.AuthenticationException;
import com.example.projectname.apps.audit.dto.response.AuditEventResponse;
import com.example.projectname.apps.audit.enums.AuditAction;
import com.example.projectname.apps.auth.model.SocialAccount;
import com.example.projectname.apps.users.model.User;
import com.example.projectname.apps.auth.repository.SocialRepo;
import com.example.projectname.apps.users.repository.UserRepo;
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
    private final SocialRepo socialAccountRepo;
    private final ApplicationEventPublisher eventPublisher;
    private final HttpServletRequest request;

    @Transactional
    public void unlinkProvider(UUID userId, String provider) {
        // 1. Fetch a FRESH user with their social accounts joined
        User user = userRepository.findByIdWithSocialAccounts(userId)
                .orElseThrow(() -> new AuthenticationException("User not found"));

        // 2. Find the specific social account to remove
        SocialAccount accountToUnlink = user.getSocialAccounts().stream()
                .filter(sa -> sa.getProvider().equalsIgnoreCase(provider))
                .findFirst()
                .orElseThrow(() -> new AuthenticationException("Social account link not found"));

        // 3. THE SAFETY RAIL: Check if they are about to lock themselves out
        boolean hasPassword = user.getPassword() != null;
        int socialCount = user.getSocialAccounts().size();

        if (!hasPassword && socialCount == 1) {
            throw new IllegalStateException(
                    "Cannot unlink your only login method. Please set a password first.");
        }

        // 4. Perform the removal
        user.getSocialAccounts().remove(accountToUnlink);
        socialAccountRepo.delete(accountToUnlink);

        publishAudit(user.getId());
        log.info("User {} successfully unlinked provider {}", user.getEmail(), provider);
    }

    private void publishAudit(UUID userId) {
        eventPublisher.publishEvent(new AuditEventResponse(
                userId,
                AuditAction.SOCIAL_UNLINK,
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                null
        ));
    }
}
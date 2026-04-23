package com.example.projectname.apps.auth.security.oauth2;

import com.example.projectname.apps.audit.dto.response.AuditEventResponse;
import com.example.projectname.apps.auth.dto.internal.CustomUserPrincipal;
import com.example.projectname.apps.audit.enums.AuditAction;
import com.example.projectname.apps.users.enums.UserRole;
import com.example.projectname.apps.auth.model.SocialAccount;
import com.example.projectname.apps.users.model.User;
import com.example.projectname.apps.auth.repository.SocialRepo;
import com.example.projectname.apps.users.repository.UserRepo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Service responsible for processing user information retrieved from OAuth2 and OIDC providers.
 * It handles identity merging by linking multiple social accounts to a single core User entity.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepo userRepository;
    private final SocialRepo socialAccountRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final HttpServletRequest request;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        return processUser(userRequest, oAuth2User);
    }

    public CustomUserPrincipal processUser(OAuth2UserRequest userRequest, OAuth2User oAuth2User) {
        String provider = userRequest.getClientRegistration().getRegistrationId().toUpperCase();
        Map<String, Object> attributes = oAuth2User.getAttributes();

        String providerId = extractProviderId(attributes, provider);
        String email = (String) attributes.get("email");

        // 1. CHECK IF USER IS ALREADY LOGGED IN (Linking Flow)
        Authentication existingAuth = SecurityContextHolder.getContext().getAuthentication();

        if (existingAuth != null && existingAuth.isAuthenticated()
                && existingAuth.getPrincipal() instanceof CustomUserPrincipal principal) {

            log.info("Linking flow detected for user: {}", principal.getUsername());
            User currentUser = principal.user();

            // Re-fetch to ensure we are in the current transaction/session
            User managedUser = userRepository.findByIdWithSocialAccounts(currentUser.getId())
                    .orElseThrow(() -> new OAuth2AuthenticationException("User session lost"));

            // Check if this specific provider is already linked to THIS user
            boolean alreadyLinked = managedUser.getSocialAccounts().stream()
                    .anyMatch(sa -> sa.getProvider().equals(provider));

            if (!alreadyLinked) {
                linkSocialAccount(managedUser, provider, providerId);
            }

            return new CustomUserPrincipal(managedUser, attributes);
        }

        // 2. STANDARD LOGIN/REGISTER FLOW
        log.info("Processing OAuth2 login for provider: {} with email: {}", provider, email);
        User user = resolveUser(email, provider, providerId, attributes);

        publishAudit(user.getId(), AuditAction.SOCIAL_LINK);
        return new CustomUserPrincipal(user, attributes);
    }

    private User resolveUser(String email, String provider, String providerId,
                             Map<String, Object> attributes) {
        return socialAccountRepository.findByProviderAndProviderIdWithUser(provider, providerId)
                .map(SocialAccount::getUser)
                .orElseGet(() -> {
                    User existingUser = userRepository.findByEmailWithSocialAccounts(email)
                            .orElseGet(() -> {
                                User user = createNewUser(attributes);
                                publishAudit(user.getId(), AuditAction.REGISTER);
                                return user;
                            });

                    if (existingUser.getFirstName() == null) {
                        existingUser.setFirstName((String) attributes.get("given_name"));
                        existingUser.setLastName((String) attributes.get("family_name"));
                        existingUser.setAvatarUrl((String) attributes.get("picture"));
                        existingUser.setEmailVerified((boolean) attributes
                                .getOrDefault("email_verified", false));

                        userRepository.saveAndFlush(existingUser);
                    }

                    linkSocialAccount(existingUser, provider, providerId);
                    return existingUser;
                });
    }

    private void linkSocialAccount(User user, String provider, String providerId) {
        // PROD CHECK: Is this social account already linked to a DIFFERENT user?
        socialAccountRepository.findByProviderAndProviderId(provider, providerId)
                .ifPresent(sa -> {
                    throw new OAuth2AuthenticationException(
                            "This " + provider + " account is already linked to another user.");
                });

        SocialAccount newAccount = new SocialAccount();
        newAccount.setProvider(provider);
        newAccount.setProviderId(providerId);
        newAccount.setUser(user);

        user.getSocialAccounts().add(newAccount);
        socialAccountRepository.save(newAccount);
        log.info("Linked new social provider [{}] to user [{}]", provider, user.getEmail());
    }

    private User createNewUser(Map<String, Object> attributes) {
        User newUser = new User();
        newUser.setEmail((String) attributes.get("email"));
        newUser.setPassword(null);
        newUser.setFirstName((String) attributes.get("given_name"));
        newUser.setLastName((String) attributes.get("family_name"));
        newUser.setAvatarUrl((String) attributes.get("picture"));
        newUser.setEmailVerified((boolean) attributes.getOrDefault("email_verified", false));
        newUser.setRole(UserRole.USER);
        return userRepository.save(newUser);
    }

    private String extractProviderId(Map<String, Object> attributes, String provider) {
        if ("GOOGLE".equals(provider)) return (String) attributes.get("sub");
        if ("GITHUB".equals(provider)) return String.valueOf(attributes.get("id"));
        return (String) attributes.get("id");
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
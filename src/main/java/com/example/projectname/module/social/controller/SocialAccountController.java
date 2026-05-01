package com.example.projectname.module.social.controller;

import com.example.projectname.module.shared.dto.response.CustomUserPrincipal;
import com.example.projectname.module.social.service.SocialAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/social")
@RequiredArgsConstructor
public class SocialAccountController {

    private final SocialAccountService socialService;

    @DeleteMapping("/unlink/{provider}")
    public ResponseEntity<Void> unlinkAccount(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable String provider) {

        socialService.unlinkProvider(principal.user().getId(), provider.toUpperCase());
        return ResponseEntity.noContent().build();
    }
}

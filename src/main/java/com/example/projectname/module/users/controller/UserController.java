package com.example.projectname.apps.users.controller;

import com.example.projectname.apps.auth.dto.internal.CustomUserPrincipal;
import com.example.projectname.apps.auth.dto.request.AccountDeletionRequest;
import com.example.projectname.apps.users.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService baseUserService;

    /**
     * Step 1: Request the deletion code.
     */
    @PostMapping("/me/delete-request")
    public ResponseEntity<Void> requestDelete(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        baseUserService.initiateDeletion(principal.user());
        return ResponseEntity.ok().build();
    }

    /**
     * Step 2: Confirm deletion with Password and OTP.
     */
    @DeleteMapping("/me")
    public ResponseEntity<Void> confirmDelete(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @RequestBody AccountDeletionRequest request) {
        baseUserService.confirmSoftDelete(
                principal.user().getId(),
                request.password(),
                request.otp()
        );
        return ResponseEntity.noContent().build();
    }
}

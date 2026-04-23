package com.example.projectname.apps.user.dto.response;

import com.example.projectname.apps.auth.dto.response.SocialResponse;

import java.util.List;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String role,
        String avatarUrl,
        boolean emailVerified,
        List<SocialResponse> socialAccounts
) {}

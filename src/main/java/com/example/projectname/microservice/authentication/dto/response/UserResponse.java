package com.example.projectname.microservice.authentication.dto.response;

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
        List<SocialAccountResponse> socialAccounts
) {}

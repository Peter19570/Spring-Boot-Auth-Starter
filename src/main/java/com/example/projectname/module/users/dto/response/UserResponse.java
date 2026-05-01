package com.example.projectname.module.users.dto.response;

import com.example.projectname.module.social.dto.response.SocialResponse;

import java.util.List;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String role,
        String avatarUrl,
        boolean emailVerified
) {}

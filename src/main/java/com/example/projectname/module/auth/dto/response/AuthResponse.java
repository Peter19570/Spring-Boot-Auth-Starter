package com.example.projectname.apps.auth.dto.response;

import com.example.projectname.apps.users.dto.response.UserResponse;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        long expiresAt,  // seconds, so frontend knows when to refresh
        UserResponse user
) {}

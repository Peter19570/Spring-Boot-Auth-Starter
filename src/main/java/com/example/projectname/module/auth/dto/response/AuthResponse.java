package com.example.projectname.module.auth.dto.response;

import com.example.projectname.module.users.dto.response.UserResponse;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        long expiresAt,  // seconds, so frontend knows when to refresh
        UserResponse user
) {}

package com.example.projectname.microservice.authentication.dto.response;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        long expiresAt,  // seconds, so frontend knows when to refresh
        UserResponse user
) {}

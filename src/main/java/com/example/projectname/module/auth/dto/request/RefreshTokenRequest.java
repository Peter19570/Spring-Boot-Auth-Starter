package com.example.projectname.apps.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
        @NotBlank String refreshToken
) {}

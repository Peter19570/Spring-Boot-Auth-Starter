package com.example.projectname.microservice.authentication.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Data Transfer Object for confirming account deletion.
 * Includes credentials and the one-time verification code.
 */
public record AccountDeletionRequest(
        String password,

        @NotBlank(message = "Verification code is required")
        @Size(min = 6, max = 6, message = "OTP must be exactly 6 characters")
        String otp
) {}

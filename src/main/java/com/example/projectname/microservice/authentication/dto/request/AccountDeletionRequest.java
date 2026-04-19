package com.example.projectname.microservice.authentication.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccountDeletionRequest(
        String password,

        @NotBlank(message = "Verification code is required")
        @Size(min = 6, max = 6, message = "OTP must be exactly 6 characters")
        String otp
) {}

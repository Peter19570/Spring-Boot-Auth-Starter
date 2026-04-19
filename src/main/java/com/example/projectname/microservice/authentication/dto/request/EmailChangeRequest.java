package com.example.projectname.microservice.authentication.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Data Transfer Object for initiating an email change request.
 * * @param newEmail The validated new email address the user intends to switch to.
 */
public record EmailChangeRequest(
        @NotBlank(message = "New email is required")
        @Email(message = "Please provide a valid email address")
        @Size(max = 150, message = "Email address is too long")
        String newEmail
) {}

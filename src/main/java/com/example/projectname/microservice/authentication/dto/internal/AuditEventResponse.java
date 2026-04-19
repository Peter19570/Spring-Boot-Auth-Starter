package com.example.projectname.microservice.authentication.dto.internal;

import com.example.projectname.microservice.authentication.enums.AuditAction;

import java.util.UUID;

/**
 * A custom event to encapsulate audit data.
 */
public record AuditEventResponse(
        UUID userId,
        AuditAction action,
        String ipAddress,
        String userAgent,
        String metadata
) {
}

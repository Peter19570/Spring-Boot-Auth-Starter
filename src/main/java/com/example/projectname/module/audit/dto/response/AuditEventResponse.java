package com.example.projectname.apps.audit.dto.response;

import com.example.projectname.apps.audit.enums.AuditAction;

import java.util.UUID;

public record AuditEventResponse(
        UUID userId,
        AuditAction action,
        String ipAddress,
        String userAgent,
        String metadata
) {
}

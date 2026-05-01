package com.example.projectname.module.audit.dto.response;

import com.example.projectname.module.audit.enums.AuditAction;

import java.util.UUID;

public record AuditEventResponse(
        UUID userId,
        AuditAction action,
        String ipAddress,
        String userAgent,
        String metadata
) {
}

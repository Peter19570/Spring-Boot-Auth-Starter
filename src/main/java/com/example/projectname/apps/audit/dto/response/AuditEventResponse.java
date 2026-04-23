package com.example.projectname.apps.audit;

import java.util.UUID;

public record AuditEventResponse(
        UUID userId,
        AuditAction action,
        String ipAddress,
        String userAgent,
        String metadata
) {
}

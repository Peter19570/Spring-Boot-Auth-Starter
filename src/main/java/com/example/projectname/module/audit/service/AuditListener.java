package com.example.projectname.apps.audit.service;

import com.example.projectname.apps.audit.model.AuditLog;
import com.example.projectname.apps.audit.repository.AuditLogRepo;
import com.example.projectname.apps.audit.dto.response.AuditEventResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuditListener {

    private final AuditLogRepo auditLogRepo;

    /**
     * Catches AuditEvents and persists them to the database.
     * Transactional and Async to ensure high performance and reliability.
     */
    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleAuditEvent(AuditEventResponse event) {
        log.debug("Logging audit action: {} for user: {}", event.action(), event.userId());

        AuditLog logEntry = AuditLog.builder()
                .userId(event.userId())
                .action(event.action())
                .ipAddress(event.ipAddress())
                .userAgent(event.userAgent())
                .metadata(event.metadata())
                .build();

        auditLogRepo.save(logEntry);
    }
}

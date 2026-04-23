package com.example.projectname.apps.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepo extends JpaRepository<AuditLog, UUID> {

    /**
     * Finds the most recent activity for a specific user.
     * Useful for the "Recent Activity" section in the UI.
     */
    List<AuditLog> findTop10ByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Finds all logs for a specific action (e.g., all LOGIN_FAILUREs for an IP).
     */
    List<AuditLog> findByIpAddressAndActionOrderByCreatedAtDesc(String ipAddress, AuditAction action);
}

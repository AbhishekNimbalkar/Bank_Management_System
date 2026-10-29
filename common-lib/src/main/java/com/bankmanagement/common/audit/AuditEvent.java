package com.bankmanagement.common.audit;

import java.time.Instant;
import java.util.UUID;

/** Message published to the audit exchange for every security- or business-relevant action. */
public record AuditEvent(
        UUID eventId,
        String tenantCode,
        String actorId,
        String actorName,
        String actorRole,
        String impersonatorId,
        String module,
        String action,
        String detail,
        String ipAddress,
        String requestId,
        String sourceService,
        Instant occurredAt) {
}

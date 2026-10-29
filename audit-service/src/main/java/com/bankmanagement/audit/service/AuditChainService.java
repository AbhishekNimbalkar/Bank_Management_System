package com.bankmanagement.audit.service;

import com.bankmanagement.audit.domain.AuditChainHead;
import com.bankmanagement.audit.domain.AuditLog;
import com.bankmanagement.audit.repository.AuditChainHeadRepository;
import com.bankmanagement.audit.repository.AuditLogRepository;
import com.bankmanagement.common.audit.AuditEvent;
import com.bankmanagement.common.security.Role;
import com.bankmanagement.common.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditChainService {

    private final AuditLogRepository logs;
    private final AuditChainHeadRepository heads;

    /** Appends one event to its tenant's chain. Idempotent on the event id. */
    @Transactional
    public void append(AuditEvent event) {
        if (event.eventId() == null || event.module() == null || event.action() == null) {
            log.warn("Discarding malformed audit event: {}", event);
            return;
        }
        if (logs.existsById(event.eventId())) {
            return; // redelivery
        }
        String tenant = SecurityUtils.isValidTenantCode(event.tenantCode()) ? event.tenantCode() : Role.PLATFORM_TENANT;

        heads.createIfAbsent(tenant);
        AuditChainHead head = heads.lock(tenant).orElseThrow();

        AuditLog entry = new AuditLog();
        entry.setId(event.eventId());
        entry.setTenantCode(tenant);
        entry.setSequence(head.getLastSequence() + 1);
        // PostgreSQL keeps microseconds; truncate before hashing so re-verification matches.
        Instant occurred = event.occurredAt() == null ? Instant.now() : event.occurredAt();
        entry.setOccurredAt(occurred.truncatedTo(ChronoUnit.MICROS));
        entry.setReceivedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        entry.setActorId(cut(event.actorId(), 64));
        entry.setActorName(cut(event.actorName(), 80));
        entry.setActorRole(cut(event.actorRole(), 10));
        entry.setImpersonatorId(cut(event.impersonatorId(), 64));
        entry.setModule(cut(event.module(), 40));
        entry.setAction(cut(event.action(), 80));
        entry.setDetail(cut(event.detail(), 500));
        entry.setIpAddress(cut(event.ipAddress(), 45));
        entry.setRequestId(cut(event.requestId(), 64));
        entry.setSourceService(cut(event.sourceService(), 40));
        entry.setPrevHash(head.getLastHash());
        entry.setHash(AuditHasher.hash(entry));
        logs.save(entry);

        head.setLastSequence(entry.getSequence());
        head.setLastHash(entry.getHash());
    }

    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}

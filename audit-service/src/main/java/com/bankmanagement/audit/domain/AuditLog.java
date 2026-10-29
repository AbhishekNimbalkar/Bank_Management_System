package com.bankmanagement.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit entry. Each entry stores the hash of the previous one in its tenant's chain, so
 * editing or deleting any row breaks verification. The id is the publisher's event id, which makes
 * redelivered messages idempotent.
 */
@Getter
@Setter
@Entity
@Immutable
@Table(name = "audit_logs", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_code", "sequence_no"}))
public class AuditLog {

    @Id
    private UUID id;

    @Column(name = "tenant_code", nullable = false, length = 40)
    private String tenantCode;

    @Column(name = "sequence_no", nullable = false)
    private long sequence;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "actor_id", length = 64)
    private String actorId;

    @Column(name = "actor_name", length = 80)
    private String actorName;

    @Column(name = "actor_role", length = 10)
    private String actorRole;

    @Column(name = "impersonator_id", length = 64)
    private String impersonatorId;

    @Column(nullable = false, length = 40)
    private String module;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(length = 500)
    private String detail;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "source_service", length = 40)
    private String sourceService;

    @Column(name = "prev_hash", nullable = false, length = 64)
    private String prevHash;

    @Column(nullable = false, length = 64)
    private String hash;
}

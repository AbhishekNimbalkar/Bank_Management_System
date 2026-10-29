package com.bankmanagement.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Last link of a tenant's chain; locked while appending so sequence numbers never collide. */
@Getter
@Setter
@Entity
@Table(name = "audit_chain_heads")
public class AuditChainHead {

    public static final String GENESIS = "GENESIS";

    @Id
    @Column(name = "tenant_code", length = 40)
    private String tenantCode;

    @Column(name = "last_sequence", nullable = false)
    private long lastSequence;

    @Column(name = "last_hash", nullable = false, length = 64)
    private String lastHash;
}

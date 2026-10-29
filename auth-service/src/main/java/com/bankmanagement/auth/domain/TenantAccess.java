package com.bankmanagement.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Sign-in gate per tenant, kept in sync by organization-service when a tenant is suspended. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tenant_access")
public class TenantAccess {

    @Id
    @Column(name = "tenant_code", length = 40)
    private String tenantCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum Status { ACTIVE, SUSPENDED }

    public TenantAccess(String tenantCode, Status status, Instant updatedAt) {
        this.tenantCode = tenantCode;
        this.status = status;
        this.updatedAt = updatedAt;
    }
}

package com.bankmanagement.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/** Entity owned by one tenant. Repositories must always filter by {@code tenantCode}. */
@Getter
@Setter
@MappedSuperclass
public abstract class TenantScopedEntity extends BaseEntity {

    @Column(name = "tenant_code", nullable = false, length = 40, updatable = false)
    private String tenantCode;
}

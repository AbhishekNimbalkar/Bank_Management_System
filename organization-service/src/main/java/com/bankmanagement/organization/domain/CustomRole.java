package com.bankmanagement.organization.domain;

import com.bankmanagement.common.persistence.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** Organization-defined role (e.g. Internal Auditor). Built-in roles are not stored here. */
@Getter
@Setter
@Entity
@Table(name = "custom_roles", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_code", "code"}))
public class CustomRole extends TenantScopedEntity {

    @Column(nullable = false, length = 10)
    private String code;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(length = 60)
    private String category;

    @Column(length = 240)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RecordStatus status = RecordStatus.ACTIVE;
}

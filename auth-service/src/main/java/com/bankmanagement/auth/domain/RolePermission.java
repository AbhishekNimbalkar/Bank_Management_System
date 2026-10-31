package com.bankmanagement.auth.domain;

import com.bankmanagement.common.persistence.TenantScopedEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

/** A tenant's override of the default rights a role has on one module, e.g. {@code BM/leaves = "CRU"}. */
@Getter
@Setter
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(name = "role_permissions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_code", "role_code", "module"}))
@AttributeOverride(name = "id", column = @Column(name = "role_permission_id"))
public class RolePermission extends TenantScopedEntity {

    @Column(name = "role_code", nullable = false, length = 10)
    private String roleCode;

    @Column(nullable = false, length = 40)
    private String module;

    @Column(nullable = false, length = 4)
    private String actions;
}

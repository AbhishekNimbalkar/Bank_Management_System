package com.bankmanagement.organization.domain;

import com.bankmanagement.common.persistence.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "banks", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_code", "code"}))
public class Bank extends TenantScopedEntity {

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 6)
    private String code;

    @Embedded
    private Location location = new Location();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RecordStatus status = RecordStatus.ACTIVE;
}

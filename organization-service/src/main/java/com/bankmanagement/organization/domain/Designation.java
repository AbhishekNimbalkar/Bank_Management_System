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

@Getter
@Setter
@Entity
@Table(name = "designations", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_code", "title"}))
public class Designation extends TenantScopedEntity {

    @Column(nullable = false, length = 60)
    private String title;

    @Column(length = 60)
    private String department;

    @Column(length = 40)
    private String grade;

    @Column(length = 240)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RecordStatus status = RecordStatus.ACTIVE;
}

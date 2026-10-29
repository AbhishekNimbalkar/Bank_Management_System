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

import java.util.UUID;

/** A physical branch; its coordinates and radius form the attendance geofence. */
@Getter
@Setter
@Entity
@Table(name = "branches", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_code", "ifsc"}))
public class Branch extends TenantScopedEntity {

    @Column(name = "bank_id", nullable = false)
    private UUID bankId;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 11)
    private String ifsc;

    @Embedded
    private Location location = new Location();

    @Column(name = "radius_meters", nullable = false)
    private int radiusMeters;

    @Column(name = "headquarters", nullable = false)
    private boolean headquarters;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RecordStatus status = RecordStatus.ACTIVE;
}

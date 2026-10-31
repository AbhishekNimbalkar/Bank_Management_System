package com.bankmanagement.organization.domain;

import com.bankmanagement.common.persistence.TenantScopedEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.util.UUID;

/** A physical branch; its coordinates and radius form the attendance geofence. */
@Getter
@Setter
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(name = "branches")
@AttributeOverride(name = "id", column = @Column(name = "branch_id"))
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

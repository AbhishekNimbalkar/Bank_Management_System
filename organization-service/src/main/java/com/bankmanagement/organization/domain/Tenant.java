package com.bankmanagement.organization.domain;

import com.bankmanagement.common.persistence.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.UUID;

/** A customer organization (bank / credit society) with the quotas set by the platform owner. */
@Getter
@Setter
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(name = "tenants")
@AttributeOverride(name = "id", column = @Column(name = "organization_id"))
public class Tenant extends BaseEntity {

    public enum Plan { STARTER, GROWTH, PRO, ENTERPRISE }

    /** ARCHIVED is terminal: data is retained for audit, nobody can sign in, it cannot be reactivated. */
    public enum Status { ACTIVE, SUSPENDED, ARCHIVED }

    @Column(nullable = false, unique = true, length = 20, updatable = false)
    private String code;

    @Column(name = "organization_name", nullable = false, length = 100)
    private String organizationName;

    @Column(name = "headquarters", length = 80)
    private String headquarters;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Plan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.ACTIVE;

    @Column(name = "max_banks", nullable = false)
    private int maxBanks;

    @Column(name = "max_branches", nullable = false)
    private int maxBranches;

    @Column(name = "max_employees", nullable = false)
    private int maxEmployees;

    @Column(name = "max_customers", nullable = false)
    private int maxCustomers;

    @Column(name = "feature_geofencing", nullable = false)
    private boolean geofencing = true;

    @Column(name = "feature_incentives", nullable = false)
    private boolean incentives = true;

    @Column(name = "feature_multi_branch", nullable = false)
    private boolean multiBranch = true;

    @Column(name = "feature_audit_export", nullable = false)
    private boolean auditExport = true;

    @Column(name = "admin_user_id")
    private UUID adminUserId;

    @Column(name = "admin_name", nullable = false, length = 80)
    private String adminName;

    @Column(name = "admin_username", nullable = false, length = 32)
    private String adminUsername;

    @Column(name = "admin_email", length = 120)
    private String adminEmail;

    @Column(name = "admin_phone", length = 10)
    private String adminPhone;

    @Column(name = "archived_at")
    private Instant archivedAt;
}

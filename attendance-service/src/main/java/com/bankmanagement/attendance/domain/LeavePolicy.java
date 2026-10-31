package com.bankmanagement.attendance.domain;

import com.bankmanagement.common.persistence.TenantScopedEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(name = "leave_policies", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_code", "code"}))
@AttributeOverride(name = "id", column = @Column(name = "leave_policy_id"))
public class LeavePolicy extends TenantScopedEntity {

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false, length = 8)
    private String code;

    @Column(name = "annual_days", nullable = false, precision = 5, scale = 1)
    private BigDecimal annualDays;

    @Column(name = "carry_forward", nullable = false)
    private boolean carryForward;

    @Column(nullable = false)
    private boolean paid = true;

    @Column(name = "half_day_allowed", nullable = false)
    private boolean halfDayAllowed = true;

    @Column(nullable = false)
    private boolean active = true;
}

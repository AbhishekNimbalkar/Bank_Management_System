package com.bankmanagement.incentive.domain;

import com.bankmanagement.common.persistence.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

/**
 * Monthly scheme. Each employee's converted volume in the covered category is compared with the
 * per-employee {@code target}: below {@code thresholdPercent} nothing is paid; above it the base
 * rate applies to all volume and the bonus rate to volume beyond 100% of target.
 */
@Getter
@Setter
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(name = "incentive_plans", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_code", "plan_month", "name"}))
public class IncentivePlan extends TenantScopedEntity {

    @Column(nullable = false, length = 60)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Enums.PlanCategory category;

    /** Calendar month in ISO form, e.g. 2026-11. */
    @Column(name = "plan_month", nullable = false, length = 7)
    private String month;

    @Column(name = "target_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal target;

    @Column(name = "threshold_percent", nullable = false)
    private int thresholdPercent;

    @Column(name = "base_rate_percent", nullable = false, precision = 5, scale = 3)
    private BigDecimal baseRatePercent;

    @Column(name = "bonus_rate_percent", nullable = false, precision = 5, scale = 3)
    private BigDecimal bonusRatePercent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Enums.PlanStatus status = Enums.PlanStatus.ACTIVE;
}

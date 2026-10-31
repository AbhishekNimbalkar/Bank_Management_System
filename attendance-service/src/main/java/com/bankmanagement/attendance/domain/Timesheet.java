package com.bankmanagement.attendance.domain;

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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(name = "timesheets")
@AttributeOverride(name = "id", column = @Column(name = "timesheet_id"))
public class Timesheet extends TenantScopedEntity {

    @Embedded
    private Subject subject;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(nullable = false, length = 200)
    private String task;

    @Column(nullable = false, precision = 4, scale = 1)
    private BigDecimal hours;

    @Column(length = 300)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Enums.TimesheetStatus status = Enums.TimesheetStatus.SUBMITTED;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;
}

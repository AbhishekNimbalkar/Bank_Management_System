package com.bankmanagement.attendance.domain;

import com.bankmanagement.common.persistence.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalTime;

/** Shift rules of one organization. */
@Getter
@Setter
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(name = "attendance_rules", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_code"}))
public class AttendanceRule extends TenantScopedEntity {

    @Column(name = "shift_start", nullable = false)
    private LocalTime shiftStart = LocalTime.of(9, 30);

    @Column(name = "grace_minutes", nullable = false)
    private int graceMinutes = 15;

    @Column(name = "half_day_hours", nullable = false, precision = 4, scale = 2)
    private BigDecimal halfDayHours = new BigDecimal("4.00");

    /** Reject GPS fixes less precise than this (meters), a basic guard against spoofed/poor locations. */
    @Column(name = "max_gps_accuracy_meters", nullable = false)
    private int maxGpsAccuracyMeters = 100;
}

package com.bankmanagement.attendance.domain;

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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/** One row per employee per day. */
@Getter
@Setter
@Entity
@Table(name = "attendance_records",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_code", "user_id", "work_date"}))
public class AttendanceRecord extends TenantScopedEntity {

    @Embedded
    private Subject subject;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "check_in")
    private LocalTime checkIn;

    @Column(name = "check_out")
    private LocalTime checkOut;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Enums.AttendanceStatus status;

    @Column(name = "distance_meters")
    private Integer distanceMeters;

    @Column(name = "hours_worked", precision = 4, scale = 2)
    private BigDecimal hoursWorked;

    @Column(length = 200)
    private String note;
}

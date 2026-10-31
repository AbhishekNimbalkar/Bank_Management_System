package com.bankmanagement.attendance.domain;

import com.bankmanagement.common.persistence.TenantScopedEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** One row per employee per day. */
@Getter
@Setter
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(name = "attendance_records",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_code", "user_id", "work_date"}))
@AttributeOverride(name = "id", column = @Column(name = "attendance_record_id"))
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Enums.AttendanceSource source = Enums.AttendanceSource.GPS;

    /** Where the employee clocked in (GPS) or worked (manual entry). */
    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    /** Who marked a MANUAL record. */
    @Column(name = "marked_by")
    private UUID markedBy;

    @Column(name = "marked_by_name", length = 80)
    private String markedByName;

    /** Last HR / Super Admin edit; the full history is in the audit log. */
    @Column(name = "edited_by")
    private UUID editedBy;

    @Column(name = "edited_by_name", length = 80)
    private String editedByName;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "edit_reason", length = 200)
    private String editReason;
}

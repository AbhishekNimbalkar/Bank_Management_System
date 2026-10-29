package com.bankmanagement.attendance.dto;

import com.bankmanagement.attendance.domain.AttendanceRecord;
import com.bankmanagement.attendance.domain.AttendanceRule;
import com.bankmanagement.attendance.domain.Enums;
import com.bankmanagement.attendance.domain.Regularization;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public final class AttendanceDto {

    private AttendanceDto() {
    }

    /** Device location at clock-in. {@code accuracyMeters} comes from the browser/phone GPS fix. */
    public record CheckInRequest(
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
            @DecimalMin("0.0") @DecimalMax("10000.0") Double accuracyMeters) {
    }

    public record Response(UUID id, UUID userId, String employeeName, String employeeCode, UUID branchId,
                           LocalDate workDate, @JsonFormat(pattern = "HH:mm") LocalTime checkIn,
                           @JsonFormat(pattern = "HH:mm") LocalTime checkOut, Enums.AttendanceStatus status,
                           Integer distanceMeters, BigDecimal hoursWorked, String note) {

        public static Response from(AttendanceRecord r) {
            return new Response(r.getId(), r.getSubject().getUserId(), r.getSubject().getEmployeeName(),
                    r.getSubject().getEmployeeCode(), r.getSubject().getBranchId(), r.getWorkDate(), r.getCheckIn(),
                    r.getCheckOut(), r.getStatus(), r.getDistanceMeters(), r.getHoursWorked(), r.getNote());
        }
    }

    public record RuleRequest(
            @NotNull @JsonFormat(pattern = "HH:mm") LocalTime shiftStart,
            @NotNull @Min(0) @Max(120) Integer graceMinutes,
            @NotNull @DecimalMin("1.0") @DecimalMax("12.0") BigDecimal halfDayHours,
            @NotNull @Min(10) @Max(500) Integer maxGpsAccuracyMeters) {
    }

    public record RuleResponse(@JsonFormat(pattern = "HH:mm") LocalTime shiftStart, int graceMinutes,
                               BigDecimal halfDayHours, int maxGpsAccuracyMeters) {

        public static RuleResponse from(AttendanceRule r) {
            return new RuleResponse(r.getShiftStart(), r.getGraceMinutes(), r.getHalfDayHours(), r.getMaxGpsAccuracyMeters());
        }
    }

    public record CorrectionRequest(
            @NotNull LocalDate workDate,
            @NotNull @JsonFormat(pattern = "HH:mm") LocalTime checkIn,
            @NotNull @JsonFormat(pattern = "HH:mm") LocalTime checkOut,
            @NotBlank @Size(min = 5, max = 300) @Pattern(regexp = "^[^<>]*$") String reason) {
    }

    public record CorrectionResponse(UUID id, UUID userId, String employeeName, String employeeCode,
                                     LocalDate workDate, @JsonFormat(pattern = "HH:mm") LocalTime checkIn,
                                     @JsonFormat(pattern = "HH:mm") LocalTime checkOut, String reason,
                                     Enums.RequestStatus status, UUID decidedBy, Instant decidedAt,
                                     String decisionNote) {

        public static CorrectionResponse from(Regularization r) {
            return new CorrectionResponse(r.getId(), r.getSubject().getUserId(), r.getSubject().getEmployeeName(),
                    r.getSubject().getEmployeeCode(), r.getWorkDate(), r.getCheckIn(), r.getCheckOut(), r.getReason(),
                    r.getStatus(), r.getDecidedBy(), r.getDecidedAt(), r.getDecisionNote());
        }
    }
}

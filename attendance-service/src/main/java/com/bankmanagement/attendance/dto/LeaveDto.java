package com.bankmanagement.attendance.dto;

import com.bankmanagement.attendance.domain.Enums;
import com.bankmanagement.attendance.domain.Holiday;
import com.bankmanagement.attendance.domain.LeavePolicy;
import com.bankmanagement.attendance.domain.LeaveRequest;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class LeaveDto {

    private LeaveDto() {
    }

    public record ApplyRequest(
            @NotNull UUID policyId,
            @NotNull LocalDate fromDate,
            @NotNull LocalDate toDate,
            @NotNull Enums.DayType dayType,
            @NotBlank @Size(min = 3, max = 300) @Pattern(regexp = "^[^<>]*$") String reason) {
    }

    public record Response(UUID id, UUID userId, String employeeName, String employeeCode, UUID policyId,
                           String policyName, String policyCode, Enums.DayType dayType, LocalDate fromDate,
                           LocalDate toDate, BigDecimal days, String reason, Enums.RequestStatus status,
                           UUID decidedBy, Instant decidedAt, String decisionNote) {

        public static Response from(LeaveRequest l, LeavePolicy p) {
            return new Response(l.getId(), l.getSubject().getUserId(), l.getSubject().getEmployeeName(),
                    l.getSubject().getEmployeeCode(), l.getPolicyId(), p == null ? null : p.getName(),
                    p == null ? null : p.getCode(), l.getDayType(), l.getFromDate(), l.getToDate(), l.getDays(),
                    l.getReason(), l.getStatus(), l.getDecidedBy(), l.getDecidedAt(), l.getDecisionNote());
        }
    }

    public record Balance(UUID policyId, String name, String code, BigDecimal allocated, BigDecimal used,
                          BigDecimal remaining, boolean paid, boolean halfDayAllowed) {
    }

    public record PolicyRequest(
            @NotBlank @Size(min = 2, max = 60) @Pattern(regexp = "^[^<>]*$") String name,
            @NotBlank @Pattern(regexp = "^[A-Z]{2,8}$", message = "2-8 upper-case letters") String code,
            @NotNull @DecimalMin("0.5") @DecimalMax("365") BigDecimal annualDays,
            boolean carryForward,
            boolean paid,
            boolean halfDayAllowed,
            boolean active) {
    }

    public record PolicyResponse(UUID id, String name, String code, BigDecimal annualDays, boolean carryForward,
                                 boolean paid, boolean halfDayAllowed, boolean active) {

        public static PolicyResponse from(LeavePolicy p) {
            return new PolicyResponse(p.getId(), p.getName(), p.getCode(), p.getAnnualDays(), p.isCarryForward(),
                    p.isPaid(), p.isHalfDayAllowed(), p.isActive());
        }
    }

    public record HolidayRequest(
            @NotNull LocalDate date,
            @NotBlank @Size(min = 2, max = 80) @Pattern(regexp = "^[^<>]*$") String name,
            @NotNull Enums.HolidayType type) {
    }

    public record HolidayResponse(UUID id, LocalDate date, String name, Enums.HolidayType type, String dayOfWeek) {

        public static HolidayResponse from(Holiday h) {
            return new HolidayResponse(h.getId(), h.getHolidayDate(), h.getName(), h.getType(),
                    h.getHolidayDate().getDayOfWeek().name());
        }
    }
}

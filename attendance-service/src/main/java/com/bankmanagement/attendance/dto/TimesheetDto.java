package com.bankmanagement.attendance.dto;

import com.bankmanagement.attendance.domain.Enums;
import com.bankmanagement.attendance.domain.Timesheet;
import com.bankmanagement.attendance.domain.TimesheetComment;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class TimesheetDto {

    private TimesheetDto() {
    }

    public record Request(
            @NotNull LocalDate workDate,
            @NotBlank @Size(min = 3, max = 200) @Pattern(regexp = "^[^<>]*$") String task,
            @NotNull @DecimalMin("0.5") @DecimalMax("16.0") @Digits(integer = 2, fraction = 1) BigDecimal hours,
            @Size(max = 300) @Pattern(regexp = "^[^<>]*$") String notes) {
    }

    public record Response(UUID timesheetId, UUID userId, String employeeName, String employeeCode, LocalDate workDate,
                           String task, BigDecimal hours, String notes, Enums.TimesheetStatus status, UUID decidedBy,
                           long commentCount) {

        public static Response from(Timesheet t, long commentCount) {
            return new Response(t.getId(), t.getSubject().getUserId(), t.getSubject().getEmployeeName(),
                    t.getSubject().getEmployeeCode(), t.getWorkDate(), t.getTask(), t.getHours(), t.getNotes(),
                    t.getStatus(), t.getDecidedBy(), commentCount);
        }
    }

    public record CommentRequest(@NotBlank @Size(min = 2, max = 500) @Pattern(regexp = "^[^<>]*$") String comment) {
    }

    public record CommentResponse(UUID timesheetCommentId, UUID timesheetId, UUID authorUserId, String authorName,
                                  String authorRole, String comment, Instant createdAt) {

        public static CommentResponse from(TimesheetComment c) {
            return new CommentResponse(c.getId(), c.getTimesheetId(), c.getAuthorUserId(), c.getAuthorName(),
                    c.getAuthorRole(), c.getBody(), c.getCreatedAt());
        }
    }
}

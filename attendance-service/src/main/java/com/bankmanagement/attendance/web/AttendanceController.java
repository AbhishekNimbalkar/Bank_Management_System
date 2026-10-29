package com.bankmanagement.attendance.web;

import com.bankmanagement.attendance.dto.AttendanceDto;
import com.bankmanagement.attendance.service.AttendanceDashboardService;
import com.bankmanagement.attendance.service.AttendanceRuleService;
import com.bankmanagement.attendance.service.AttendanceService;
import com.bankmanagement.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Tag(name = "Attendance", description = "Geofenced clock-in/out, monthly records and shift rules")
@RestController
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final AttendanceRuleService ruleService;
    private final AttendanceDashboardService dashboardService;

    @Operation(summary = "Attendance for a month (own, branch or organization depending on role)")
    @PreAuthorize("hasAuthority('attendance:R')")
    @GetMapping("/api/v1/attendance")
    public ApiResponse<List<AttendanceDto.Response>> list(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            @RequestParam(required = false) UUID userId) {
        return ApiResponse.ok(attendanceService.list(month, userId));
    }

    @Operation(summary = "Export a month of attendance as CSV")
    @PreAuthorize("hasAuthority('attendance:R')")
    @GetMapping("/api/v1/attendance/export")
    public ResponseEntity<byte[]> export(@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                                         @RequestParam(required = false) UUID userId) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("attendance-" + month + ".csv").build().toString())
                .body(attendanceService.exportCsv(month, userId));
    }

    @Operation(summary = "Dashboard: today's headcount in scope, my punch, approvals waiting for me")
    @PreAuthorize("hasAuthority('dashboard:R')")
    @GetMapping("/api/v1/attendance/dashboard")
    public ApiResponse<AttendanceDashboardService.Summary> dashboard() {
        return ApiResponse.ok(dashboardService.summary());
    }

    @Operation(summary = "My attendance record for today (empty data if none)")
    @PreAuthorize("hasAuthority('attendance:R')")
    @GetMapping("/api/v1/attendance/today")
    public ApiResponse<AttendanceDto.Response> today() {
        return ApiResponse.ok(attendanceService.today());
    }

    @Operation(summary = "Clock in from the device location (must be inside the branch geofence)")
    @PreAuthorize("hasAuthority('attendance:C')")
    @PostMapping("/api/v1/attendance/check-in")
    public ApiResponse<AttendanceDto.Response> checkIn(@Valid @RequestBody AttendanceDto.CheckInRequest request) {
        return ApiResponse.ok("Clocked in.", attendanceService.checkIn(request));
    }

    @Operation(summary = "Clock out")
    @PreAuthorize("hasAuthority('attendance:C')")
    @PostMapping("/api/v1/attendance/check-out")
    public ApiResponse<AttendanceDto.Response> checkOut() {
        return ApiResponse.ok("Clocked out.", attendanceService.checkOut());
    }

    @Operation(summary = "Shift rules (start, grace, half-day threshold, GPS accuracy)")
    @PreAuthorize("hasAuthority('attendance:R')")
    @GetMapping("/api/v1/attendance-rules")
    public ApiResponse<AttendanceDto.RuleResponse> rules() {
        return ApiResponse.ok(ruleService.get());
    }

    @Operation(summary = "Update shift rules")
    @PreAuthorize("hasAuthority('policies:U')")
    @PutMapping("/api/v1/attendance-rules")
    public ApiResponse<AttendanceDto.RuleResponse> updateRules(@Valid @RequestBody AttendanceDto.RuleRequest request) {
        return ApiResponse.ok("Attendance rules updated.", ruleService.update(request));
    }
}

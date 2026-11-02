package com.bankmanagement.attendance.web;

import com.bankmanagement.attendance.dto.DecisionRequest;
import com.bankmanagement.attendance.dto.LeaveDto;
import com.bankmanagement.attendance.service.HolidayService;
import com.bankmanagement.attendance.service.LeavePolicyService;
import com.bankmanagement.attendance.service.LeaveService;
import com.bankmanagement.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Leaves", description = "Leave requests, balances, policies and the holiday calendar")
@Validated
@RestController
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveService leaveService;
    private final LeavePolicyService policyService;
    private final HolidayService holidayService;

    @Operation(summary = "List leave requests in scope")
    @PreAuthorize("hasAuthority('leaves:R')")
    @GetMapping("/api/v1/leaves")
    public ApiResponse<List<LeaveDto.Response>> list() {
        return ApiResponse.ok(leaveService.list());
    }

    @Operation(summary = "My leave balances for the current year")
    @PreAuthorize("hasAuthority('leaves:R')")
    @GetMapping("/api/v1/leaves/balances")
    public ApiResponse<List<LeaveDto.Balance>> balances() {
        return ApiResponse.ok(leaveService.myBalances());
    }

    @Operation(summary = "Apply for leave (Sundays and full-day holidays are not charged)")
    @PreAuthorize("hasAuthority('leaves:C')")
    @PostMapping("/api/v1/leaves")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<LeaveDto.Response> apply(@Valid @RequestBody LeaveDto.ApplyRequest request) {
        return ApiResponse.ok("Leave request submitted.", leaveService.apply(request));
    }

    @Operation(summary = "Approve or reject a leave request (senior in scope only)")
    @PreAuthorize("hasAuthority('leaves:U')")
    @PatchMapping("/api/v1/leaves/{id}/decision")
    public ApiResponse<LeaveDto.Response> decide(@PathVariable UUID id, @Valid @RequestBody DecisionRequest request) {
        return ApiResponse.ok("Decision recorded.", leaveService.decide(id, request));
    }

    @Operation(summary = "Cancel my pending leave request")
    @PreAuthorize("hasAuthority('leaves:D')")
    @PostMapping("/api/v1/leaves/{id}/cancel")
    public ApiResponse<Void> cancel(@PathVariable UUID id) {
        leaveService.cancel(id);
        return ApiResponse.message("Leave request cancelled.");
    }

    @Operation(summary = "Leave policies")
    @PreAuthorize("hasAuthority('policies:R')")
    @GetMapping("/api/v1/leave-policies")
    public ApiResponse<List<LeaveDto.PolicyResponse>> policies() {
        return ApiResponse.ok(policyService.list());
    }

    @Operation(summary = "Create a leave policy")
    @PreAuthorize("hasAuthority('policies:C')")
    @PostMapping("/api/v1/leave-policies")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<LeaveDto.PolicyResponse> createPolicy(@Valid @RequestBody LeaveDto.PolicyRequest request) {
        return ApiResponse.ok("Leave policy created.", policyService.create(request));
    }

    @Operation(summary = "Update a leave policy")
    @PreAuthorize("hasAuthority('policies:U')")
    @PutMapping("/api/v1/leave-policies/{id}")
    public ApiResponse<LeaveDto.PolicyResponse> updatePolicy(@PathVariable UUID id,
                                                            @Valid @RequestBody LeaveDto.PolicyRequest request) {
        return ApiResponse.ok("Leave policy updated.", policyService.update(id, request));
    }

    @Operation(summary = "Holiday calendar of a year")
    @PreAuthorize("hasAuthority('holidays:R')")
    @GetMapping("/api/v1/holidays")
    public ApiResponse<List<LeaveDto.HolidayResponse>> holidays(@RequestParam @Min(2000) @Max(2100) int year) {
        return ApiResponse.ok(holidayService.list(year));
    }

    @Operation(summary = "Add a holiday")
    @PreAuthorize("hasAuthority('holidays:C')")
    @PostMapping("/api/v1/holidays")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<LeaveDto.HolidayResponse> createHoliday(@Valid @RequestBody LeaveDto.HolidayRequest request) {
        return ApiResponse.ok("Holiday added.", holidayService.create(request));
    }

    @Operation(summary = "Edit a holiday")
    @PreAuthorize("hasAuthority('holidays:U')")
    @PutMapping("/api/v1/holidays/{id}")
    public ApiResponse<LeaveDto.HolidayResponse> updateHoliday(@PathVariable UUID id,
                                                              @Valid @RequestBody LeaveDto.HolidayRequest request) {
        return ApiResponse.ok("Holiday updated.", holidayService.update(id, request));
    }

    @Operation(summary = "Remove a holiday")
    @PreAuthorize("hasAuthority('holidays:D')")
    @DeleteMapping("/api/v1/holidays/{id}")
    public ApiResponse<Void> deleteHoliday(@PathVariable UUID id) {
        holidayService.delete(id);
        return ApiResponse.message("Holiday removed.");
    }
}

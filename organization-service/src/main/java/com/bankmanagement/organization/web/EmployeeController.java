package com.bankmanagement.organization.web;

import com.bankmanagement.common.api.ApiResponse;
import com.bankmanagement.common.api.PageResponse;
import com.bankmanagement.organization.dto.EmployeeDto;
import com.bankmanagement.organization.service.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Employees", description = "Staff profiles, salary accounts and login administration")
@RestController
@RequestMapping("/api/v1/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @Operation(summary = "Search employees (Branch Managers see only their branch)")
    @PreAuthorize("hasAuthority('employees:R')")
    @GetMapping
    public ApiResponse<PageResponse<EmployeeDto.Response>> list(@RequestParam(required = false) String role,
                                                                @RequestParam(required = false) String search,
                                                                @RequestParam(defaultValue = "0") int page,
                                                                @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(employeeService.list(role, search, page, size));
    }

    @Operation(summary = "My own employee profile")
    @GetMapping("/me")
    public ApiResponse<EmployeeDto.Response> me() {
        return ApiResponse.ok(employeeService.me());
    }

    @Operation(summary = "Get an employee")
    @PreAuthorize("hasAuthority('employees:R')")
    @GetMapping("/{id}")
    public ApiResponse<EmployeeDto.Response> get(@PathVariable UUID id) {
        return ApiResponse.ok(employeeService.get(id));
    }

    @Operation(summary = "Onboard an employee and create their login (returns a one-time password)")
    @PreAuthorize("hasAuthority('employees:C')")
    @PostMapping
    public ResponseEntity<ApiResponse<EmployeeDto.Created>> create(@Valid @RequestBody EmployeeDto.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok("Employee created.", employeeService.create(request)));
    }

    @Operation(summary = "Update an employee profile")
    @PreAuthorize("hasAuthority('employees:U')")
    @PutMapping("/{id}")
    public ApiResponse<EmployeeDto.Response> update(@PathVariable UUID id,
                                                    @Valid @RequestBody EmployeeDto.UpdateRequest request) {
        return ApiResponse.ok("Employee updated.", employeeService.update(id, request));
    }

    @Operation(summary = "Transfer to another branch and/or change role and designation")
    @PreAuthorize("hasAuthority('employees:U')")
    @PatchMapping("/{id}/assignment")
    public ApiResponse<EmployeeDto.Response> reassign(@PathVariable UUID id,
                                                      @Valid @RequestBody EmployeeDto.AssignmentRequest request) {
        return ApiResponse.ok("Employee reassigned. They must sign in again.", employeeService.reassign(id, request));
    }

    @Operation(summary = "Activate or deactivate an employee (also their login)")
    @PreAuthorize("hasAuthority('employees:U')")
    @PatchMapping("/{id}/status")
    public ApiResponse<Void> status(@PathVariable UUID id, @Valid @RequestBody EmployeeDto.StatusRequest request) {
        employeeService.setStatus(id, request.status());
        return ApiResponse.message("Employee status updated.");
    }

    @Operation(summary = "Lock or unlock the employee's login")
    @PreAuthorize("hasAuthority('employees:U')")
    @PatchMapping("/{id}/lock")
    public ApiResponse<Void> lock(@PathVariable UUID id, @Valid @RequestBody EmployeeDto.LockRequest request) {
        employeeService.setLocked(id, request.locked());
        return ApiResponse.message(request.locked() ? "Account locked." : "Account unlocked.");
    }

    @Operation(summary = "Reset the employee's password to a one-time password")
    @PreAuthorize("hasAuthority('employees:U')")
    @PostMapping("/{id}/reset-password")
    public ResponseEntity<ApiResponse<EmployeeDto.TemporaryPassword>> resetPassword(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok("Password reset.", employeeService.resetPassword(id)));
    }
}

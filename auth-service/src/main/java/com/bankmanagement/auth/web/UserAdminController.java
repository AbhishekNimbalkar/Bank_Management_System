package com.bankmanagement.auth.web;

import com.bankmanagement.auth.dto.CreateUserRequest;
import com.bankmanagement.auth.dto.CreatedUserResponse;
import com.bankmanagement.auth.dto.TemporaryPasswordResponse;
import com.bankmanagement.auth.dto.UserAssignmentRequest;
import com.bankmanagement.auth.dto.UserLockRequest;
import com.bankmanagement.auth.dto.UserStatusRequest;
import com.bankmanagement.auth.service.UserAdminService;
import com.bankmanagement.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "User administration", description = "Login accounts: create, reset password, lock, activate")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserAdminService userAdminService;

    @Operation(summary = "Create a login account (returns a one-time password)")
    @PreAuthorize("hasAuthority('employees:C')")
    @PostMapping
    public ResponseEntity<ApiResponse<CreatedUserResponse>> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok("User created.", userAdminService.create(request)));
    }

    @Operation(summary = "Reset a user's password to a new one-time password")
    @PreAuthorize("hasAuthority('employees:U')")
    @PostMapping("/{userId}/reset-password")
    public ResponseEntity<ApiResponse<TemporaryPasswordResponse>> resetPassword(@PathVariable UUID userId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok("Password reset.", userAdminService.resetPassword(userId)));
    }

    @Operation(summary = "Lock or unlock a user account")
    @PreAuthorize("hasAuthority('employees:U')")
    @PatchMapping("/{userId}/lock")
    public ApiResponse<Void> lock(@PathVariable UUID userId, @Valid @RequestBody UserLockRequest request) {
        userAdminService.setLocked(userId, request.locked());
        return ApiResponse.message(request.locked() ? "Account locked." : "Account unlocked.");
    }

    @Operation(summary = "Change a user's role and/or branch (signs the user out)")
    @PreAuthorize("hasAuthority('employees:U')")
    @PatchMapping("/{userId}/assignment")
    public ApiResponse<Void> assignment(@PathVariable UUID userId, @Valid @RequestBody UserAssignmentRequest request) {
        userAdminService.updateAssignment(userId, request.role(), request.branchId());
        return ApiResponse.message("Assignment updated.");
    }

    @Operation(summary = "Activate or deactivate a user account")
    @PreAuthorize("hasAuthority('employees:U')")
    @PatchMapping("/{userId}/status")
    public ApiResponse<Void> status(@PathVariable UUID userId, @Valid @RequestBody UserStatusRequest request) {
        userAdminService.setStatus(userId, request.status());
        return ApiResponse.message("Status updated.");
    }
}

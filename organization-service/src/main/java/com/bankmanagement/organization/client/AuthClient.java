package com.bankmanagement.organization.client;

import com.bankmanagement.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.UUID;

/** Calls auth-service with the caller's own token (see FeignSupportConfig). */
@FeignClient(name = "auth-service")
public interface AuthClient {

    @PostMapping("/api/v1/users")
    ApiResponse<CreatedUser> createUser(@RequestBody CreateUserCommand command);

    @PostMapping("/api/v1/users/{userId}/reset-password")
    ApiResponse<TemporaryPassword> resetPassword(@PathVariable("userId") UUID userId);

    /** Platform owner acting inside a specific organization (explicit tenant header). */
    @PostMapping("/api/v1/users/{userId}/reset-password")
    ApiResponse<TemporaryPassword> resetPasswordInTenant(@RequestHeader("X-Tenant-Code") String tenantCode,
                                                         @PathVariable("userId") UUID userId);

    @PostMapping("/api/v1/auth/impersonate/{userId}")
    ApiResponse<Object> impersonate(@PathVariable("userId") UUID userId);

    @PatchMapping("/api/v1/users/{userId}/lock")
    ApiResponse<Void> setLocked(@PathVariable("userId") UUID userId, @RequestBody LockCommand command);

    @PatchMapping("/api/v1/users/{userId}/status")
    ApiResponse<Void> setStatus(@PathVariable("userId") UUID userId, @RequestBody StatusCommand command);

    @PatchMapping("/api/v1/users/{userId}/assignment")
    ApiResponse<Void> updateAssignment(@PathVariable("userId") UUID userId, @RequestBody AssignmentCommand command);

    @PutMapping("/api/v1/admin/tenants/{tenantCode}/status")
    ApiResponse<Void> setTenantStatus(@PathVariable("tenantCode") String tenantCode,
                                      @RequestBody StatusCommand command);

    record CreateUserCommand(String tenantCode, String username, String displayName, String email, String role,
                             UUID employeeId, String employeeCode, UUID branchId, String password) {
        @Override
        public String toString() {
            return "CreateUserCommand[" + tenantCode + "/" + username + "]";
        }
    }

    record CreatedUser(UUID userId, String username, String temporaryPassword) {
    }

    record TemporaryPassword(String username, String temporaryPassword) {
    }

    record LockCommand(boolean locked) {
    }

    record StatusCommand(String status) {
    }

    record AssignmentCommand(String role, UUID branchId) {
    }
}

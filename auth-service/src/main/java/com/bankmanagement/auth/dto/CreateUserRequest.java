package com.bankmanagement.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Called by organization-service when an employee (or a tenant's first admin) is onboarded. */
public record CreateUserRequest(
        @Schema(description = "Only honoured for the platform owner provisioning a new tenant")
        @Pattern(regexp = "^[a-z][a-z0-9]{2,19}$") String tenantCode,
        @NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9._-]{2,31}$",
                message = "3-32 characters: lower-case letters, numbers, dot, dash or underscore") String username,
        @NotBlank @Size(min = 2, max = 80) String displayName,
        @Email @Size(max = 120) String email,
        @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9]{1,9}$") String role,
        UUID employeeId,
        @Size(max = 20) String employeeCode,
        UUID branchId,
        @Schema(description = "Optional initial password; a strong one-time password is generated when empty")
        @Size(max = 128) String password) {

    @Override
    public String toString() {
        return "CreateUserRequest[tenantCode=" + tenantCode + ", username=" + username + ", role=" + role + "]";
    }
}

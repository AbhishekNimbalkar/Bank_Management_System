package com.bankmanagement.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record PermissionUpdateRequest(
        @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9]{1,9}$") String role,
        @NotBlank @Pattern(regexp = "^[a-zA-Z]{2,40}$") String module,
        @NotBlank @Pattern(regexp = "^[CRUD]$", message = "must be one of C, R, U, D") String action,
        @NotNull Boolean granted) {
}

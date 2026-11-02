package com.bankmanagement.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

/** New role and/or branch of a user after a transfer or promotion. */
public record UserAssignmentRequest(
        @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9]{1,9}$") String role,
        @NotNull UUID branchId) {
}

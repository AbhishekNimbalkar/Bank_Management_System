package com.bankmanagement.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @Schema(example = "sahyadri", description = "Organization code; use 'platform' for the platform owner")
        @NotBlank @Size(max = 40) String tenantCode,
        @Schema(example = "admin") @NotBlank @Size(max = 64) String username,
        @Schema(example = "Admin@Demo2026") @NotBlank @Size(max = 256) String password) {

    @Override
    public String toString() {
        return "LoginRequest[tenantCode=" + tenantCode + ", username=" + username + ", password=***]";
    }
}

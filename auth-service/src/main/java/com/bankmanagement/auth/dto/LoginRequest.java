package com.bankmanagement.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Usernames are unique across the whole platform, so the organization is resolved from the account. */
public record LoginRequest(
        @Schema(example = "ramesh.k") @NotBlank @Size(max = 64) String username,
        @Schema(example = "Admin@Demo2026") @NotBlank @Size(max = 256) String password) {

    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", password=***]";
    }
}

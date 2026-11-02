package com.bankmanagement.auth.dto;

import java.util.Set;
import java.util.UUID;

public record UserProfile(
        UUID userId,
        String username,
        String name,
        String email,
        String role,
        String tenantCode,
        UUID employeeId,
        String employeeCode,
        UUID branchId,
        boolean mustChangePassword,
        boolean impersonated,
        Set<String> permissions) {
}

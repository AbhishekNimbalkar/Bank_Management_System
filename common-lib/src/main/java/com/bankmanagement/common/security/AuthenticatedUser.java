package com.bankmanagement.common.security;

import java.util.Set;
import java.util.UUID;

/**
 * Identity of the caller, reconstructed from a verified access token.
 *
 * @param tenantCode tenant the token was issued for ({@link Role#PLATFORM_TENANT} for the platform owner)
 * @param impersonatorId set when a platform owner is running an audited support session
 * @param permissions authorities in the form {@code module:ACTION}, e.g. {@code leaves:U}
 */
public record AuthenticatedUser(
        UUID userId,
        UUID employeeId,
        String username,
        String name,
        String employeeCode,
        String tenantCode,
        String role,
        UUID branchId,
        UUID impersonatorId,
        Set<String> permissions) {

    public boolean isPlatformOwner() {
        return Role.SSA.name().equals(role);
    }

    public boolean hasPermission(String module, char action) {
        return isPlatformOwner() || permissions.contains(module + ":" + action);
    }

    public int rank() {
        return Role.rankOf(role);
    }

    public boolean isImpersonated() {
        return impersonatorId != null;
    }
}

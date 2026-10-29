package com.bankmanagement.organization.dto;

import java.util.UUID;

/** Cross-organization views for the platform owner. */
public final class PlatformDto {

    private PlatformDto() {
    }

    public record Stats(long organizations, long activeOrganizations, long suspendedOrganizations, long banks,
                        long branches, long activeEmployees) {
    }

    public record BankAllocation(UUID id, String tenantCode, String organizationName, String name, String code,
                                 String city, String state, String status, long branchCount, int maxBranchesAllowed,
                                 long activeStaff) {
    }

    public record BranchAllocation(UUID id, String tenantCode, String organizationName, UUID bankId, String bankName,
                                   String name, String ifsc, String city, double latitude, double longitude,
                                   int radiusMeters, boolean headquarters, String status, long activeStaff) {
    }

    public record AdminPasswordReset(String tenantCode, String adminUsername, String temporaryPassword) {
    }
}

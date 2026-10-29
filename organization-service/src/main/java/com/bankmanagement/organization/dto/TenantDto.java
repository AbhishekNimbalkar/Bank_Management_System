package com.bankmanagement.organization.dto;

import com.bankmanagement.organization.domain.Tenant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class TenantDto {

    private TenantDto() {
    }

    public record Limits(
            @NotNull @Min(1) @Max(1000) Integer maxBanks,
            @NotNull @Min(1) @Max(10000) Integer maxBranches,
            @NotNull @Min(1) @Max(100000) Integer maxEmployees,
            @NotNull @Min(1) @Max(1000000) Integer maxCustomers) {
    }

    public record Features(boolean geofencing, boolean incentives, boolean multiBranch, boolean auditExport) {
    }

    public record Admin(
            @NotBlank @Size(min = 2, max = 80) @Pattern(regexp = Patterns.SAFE_TEXT) String name,
            @NotBlank @Pattern(regexp = Patterns.USERNAME) String username,
            @NotBlank @Email @Size(max = 120) String email,
            @NotBlank @Pattern(regexp = Patterns.MOBILE) String phone,
            @Size(max = 128) String initialPassword) {

        @Override
        public String toString() {
            return "Admin[username=" + username + "]";
        }
    }

    /** Provisions an organization with its first bank, headquarters branch and Super Admin. */
    public record CreateRequest(
            @NotBlank @Pattern(regexp = Patterns.TENANT_CODE, message = "3-20 lower-case letters/digits, starting with a letter")
            String code,
            @NotBlank @Size(min = 2, max = 100) @Pattern(regexp = Patterns.SAFE_TEXT) String organizationName,
            @NotNull Tenant.Plan plan,
            @NotNull @Valid Limits limits,
            @NotNull Features features,
            @NotNull @Valid Admin admin,
            @NotNull @Valid LocationDto headquarters) {
    }

    public record UpdateRequest(
            @NotNull Tenant.Plan plan,
            @NotNull @Valid Limits limits,
            @NotNull Features features,
            @NotNull Tenant.Status status) {
    }

    public record Usage(long banks, long branches, long activeEmployees) {
    }

    /** Organization profile and admin contact details (no effect on quotas or access). */
    public record ProfileRequest(
            @NotBlank @Size(min = 2, max = 100) @Pattern(regexp = Patterns.SAFE_TEXT) String organizationName,
            @NotBlank @Size(max = 80) @Pattern(regexp = Patterns.SAFE_TEXT) String headquarters,
            @NotBlank @Size(min = 2, max = 80) @Pattern(regexp = Patterns.SAFE_TEXT) String adminName,
            @NotBlank @Email @Size(max = 120) String adminEmail,
            @NotBlank @Pattern(regexp = Patterns.MOBILE) String adminPhone) {
    }

    /** Archiving is irreversible, so the caller must type the organization code to confirm. */
    public record ArchiveRequest(@NotBlank String confirmCode,
                                 @NotBlank @Size(min = 5, max = 200) @Pattern(regexp = Patterns.SAFE_TEXT) String reason) {
    }

    public record Response(UUID id, String code, String organizationName, String headquarters, Tenant.Plan plan,
                           Tenant.Status status, Limits limits, Features features, Usage usage, String adminName,
                           String adminUsername, String adminEmail, String adminPhone, UUID adminUserId,
                           Instant createdAt) {

        public static Response from(Tenant t, Usage usage) {
            return new Response(t.getId(), t.getCode(), t.getOrganizationName(), t.getHeadquarters(), t.getPlan(),
                    t.getStatus(),
                    new Limits(t.getMaxBanks(), t.getMaxBranches(), t.getMaxEmployees(), t.getMaxCustomers()),
                    new Features(t.isGeofencing(), t.isIncentives(), t.isMultiBranch(), t.isAuditExport()),
                    usage, t.getAdminName(), t.getAdminUsername(), t.getAdminEmail(), t.getAdminPhone(),
                    t.getAdminUserId(), t.getCreatedAt());
        }
    }

    public record Provisioned(Response tenant, String adminUsername, String temporaryPassword) {
    }

    /** Minimal view any signed-in user's services may read for their own organization. */
    public record Entitlements(boolean active, int maxCustomers, Features features) {
    }

    /** Quota view available to the organization's own administrators. */
    public record Quota(Limits limits, Usage usage, boolean canAddBank, boolean canAddBranch, boolean canAddEmployee) {
    }
}

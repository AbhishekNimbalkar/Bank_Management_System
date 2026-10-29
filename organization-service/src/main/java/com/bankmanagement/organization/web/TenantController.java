package com.bankmanagement.organization.web;

import com.bankmanagement.common.api.ApiResponse;
import com.bankmanagement.organization.dto.TenantDto;
import com.bankmanagement.organization.dto.PlatformDto;
import com.bankmanagement.organization.service.OrganizationDashboardService;
import com.bankmanagement.organization.service.PlatformService;
import org.springframework.web.bind.annotation.RequestParam;
import com.bankmanagement.organization.service.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Platform - organizations", description = "Platform owner: provision organizations and set quotas")
@RestController
@RequestMapping("/api/v1/platform")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;
    private final OrganizationDashboardService dashboardService;
    private final PlatformService platformService;

    @Operation(summary = "List organizations with quota usage")
    @PreAuthorize("hasRole('SSA')")
    @GetMapping("/tenants")
    public ApiResponse<List<TenantDto.Response>> list() {
        return ApiResponse.ok(tenantService.list());
    }

    @Operation(summary = "Get one organization")
    @PreAuthorize("hasRole('SSA')")
    @GetMapping("/tenants/{code}")
    public ApiResponse<TenantDto.Response> get(@PathVariable String code) {
        return ApiResponse.ok(tenantService.get(code));
    }

    @Operation(summary = "Provision an organization with its bank, headquarters and Super Admin login")
    @PreAuthorize("hasRole('SSA')")
    @PostMapping("/tenants")
    public ResponseEntity<ApiResponse<TenantDto.Provisioned>> create(@Valid @RequestBody TenantDto.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok("Organization provisioned.", tenantService.create(request)));
    }

    @Operation(summary = "Update plan, limits, features or status (suspend / activate)")
    @PreAuthorize("hasRole('SSA')")
    @PutMapping("/tenants/{code}")
    public ApiResponse<TenantDto.Response> update(@PathVariable String code,
                                                  @Valid @RequestBody TenantDto.UpdateRequest request) {
        return ApiResponse.ok("Organization updated.", tenantService.update(code, request));
    }

    @Operation(summary = "Edit organization name, headquarters and admin contact")
    @PreAuthorize("hasRole('SSA')")
    @PutMapping("/tenants/{code}/profile")
    public ApiResponse<TenantDto.Response> updateProfile(@PathVariable String code,
                                                         @Valid @RequestBody TenantDto.ProfileRequest request) {
        return ApiResponse.ok("Organization profile updated.", tenantService.updateProfile(code, request));
    }

    @Operation(summary = "Archive an organization (irreversible; data retained, sign-in blocked)")
    @PreAuthorize("hasRole('SSA')")
    @PostMapping("/tenants/{code}/archive")
    public ApiResponse<TenantDto.Response> archive(@PathVariable String code,
                                                   @Valid @RequestBody TenantDto.ArchiveRequest request) {
        return ApiResponse.ok("Organization archived.", tenantService.archive(code, request));
    }

    @Operation(summary = "Platform-wide statistics")
    @PreAuthorize("hasRole('SSA')")
    @GetMapping("/stats")
    public ApiResponse<PlatformDto.Stats> stats() {
        return ApiResponse.ok(platformService.stats());
    }

    @Operation(summary = "All banks across organizations with branch allocation usage")
    @PreAuthorize("hasRole('SSA')")
    @GetMapping("/banks")
    public ApiResponse<List<PlatformDto.BankAllocation>> allBanks(@RequestParam(required = false) String tenantCode) {
        return ApiResponse.ok(platformService.banks(tenantCode));
    }

    @Operation(summary = "All branches across organizations")
    @PreAuthorize("hasRole('SSA')")
    @GetMapping("/branches")
    public ApiResponse<List<PlatformDto.BranchAllocation>> allBranches(@RequestParam(required = false) String tenantCode) {
        return ApiResponse.ok(platformService.branches(tenantCode));
    }

    @Operation(summary = "Issue a new one-time password for an organization's Super Admin")
    @PreAuthorize("hasRole('SSA')")
    @PostMapping("/tenants/{code}/reset-admin-password")
    public ResponseEntity<ApiResponse<PlatformDto.AdminPasswordReset>> resetAdminPassword(@PathVariable String code) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok("Admin password reset.", platformService.resetAdminPassword(code)));
    }

    @Operation(summary = "Open an audited 30-minute support session as the organization's Super Admin")
    @PreAuthorize("hasRole('SSA')")
    @PostMapping("/tenants/{code}/support-session")
    public ResponseEntity<ApiResponse<Object>> supportSession(@PathVariable String code) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok("Support session opened.", platformService.supportSession(code)));
    }

    @Operation(summary = "Dashboard: branches, staff in scope, my branch, latest announcements")
    @PreAuthorize("hasAuthority('dashboard:R')")
    @GetMapping("/dashboard")
    public ApiResponse<OrganizationDashboardService.Summary> dashboard() {
        return ApiResponse.ok(dashboardService.summary());
    }

    @Operation(summary = "Feature flags and customer limit of the caller's own organization")
    @GetMapping("/my-entitlements")
    public ApiResponse<TenantDto.Entitlements> myEntitlements() {
        return ApiResponse.ok(tenantService.myEntitlements());
    }

    @Operation(summary = "Quota and usage of the caller's own organization")
    @PreAuthorize("hasAuthority('branches:R')")
    @GetMapping("/my-quota")
    public ApiResponse<TenantDto.Quota> myQuota() {
        return ApiResponse.ok(tenantService.myQuota());
    }
}

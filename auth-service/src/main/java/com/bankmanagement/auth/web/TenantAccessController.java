package com.bankmanagement.auth.web;

import com.bankmanagement.auth.dto.TenantStatusRequest;
import com.bankmanagement.auth.service.TenantAccessService;
import com.bankmanagement.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Organization access", description = "Platform owner: enable or suspend sign-in for an organization")
@RestController
@RequestMapping("/api/v1/admin/tenants")
@RequiredArgsConstructor
public class TenantAccessController {

    private final TenantAccessService tenantAccessService;

    @Operation(summary = "Set organization sign-in status")
    @PreAuthorize("hasRole('SSA')")
    @PutMapping("/{tenantCode}/status")
    public ApiResponse<Void> setStatus(@PathVariable String tenantCode, @Valid @RequestBody TenantStatusRequest request) {
        tenantAccessService.setStatus(tenantCode, request.status());
        return ApiResponse.message("Organization access updated.");
    }
}

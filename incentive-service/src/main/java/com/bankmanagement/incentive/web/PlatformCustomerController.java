package com.bankmanagement.incentive.web;

import com.bankmanagement.common.api.ApiResponse;
import com.bankmanagement.common.api.PageResponse;
import com.bankmanagement.incentive.domain.Enums;
import com.bankmanagement.incentive.service.PlatformCustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Platform - customers", description = "Platform owner: customer business across all organizations")
@RestController
@RequestMapping("/api/v1/platform/customers")
@RequiredArgsConstructor
public class PlatformCustomerController {

    private final PlatformCustomerService platformCustomerService;

    @Operation(summary = "Customer records across organizations (mobile numbers masked)")
    @PreAuthorize("hasRole('SSA')")
    @GetMapping
    public ApiResponse<PageResponse<PlatformCustomerService.PlatformCustomer>> search(
            @RequestParam(required = false) String tenantCode,
            @RequestParam(required = false) Enums.CustomerStatus status,
            @RequestParam(required = false) Enums.ProductType productType,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(platformCustomerService.search(tenantCode, status, productType, search, page, size));
    }

    @Operation(summary = "Records, conversions and converted volume per organization")
    @PreAuthorize("hasRole('SSA')")
    @GetMapping("/overview")
    public ApiResponse<PlatformCustomerService.Overview> overview() {
        return ApiResponse.ok(platformCustomerService.overview());
    }
}

package com.bankmanagement.audit.client;

import com.bankmanagement.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "organization-service")
public interface OrganizationClient {

    @GetMapping("/api/v1/platform/my-entitlements")
    ApiResponse<Entitlements> entitlements();

    record Features(boolean geofencing, boolean incentives, boolean multiBranch, boolean auditExport) {
    }

    record Entitlements(boolean active, int maxCustomers, Features features) {
    }
}

package com.bankmanagement.attendance.client;

import com.bankmanagement.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "organization-service")
public interface OrganizationClient {

    @GetMapping("/api/v1/branches/{branchId}/geofence")
    ApiResponse<Geofence> geofence(@PathVariable("branchId") UUID branchId);

    @GetMapping("/api/v1/platform/my-entitlements")
    ApiResponse<Entitlements> entitlements();

    record Features(boolean geofencing, boolean incentives, boolean multiBranch, boolean auditExport) {
    }

    record Entitlements(boolean active, int maxCustomers, Features features) {
    }

    record Geofence(UUID branchId, String name, double latitude, double longitude, int radiusMeters, boolean active) {
    }
}

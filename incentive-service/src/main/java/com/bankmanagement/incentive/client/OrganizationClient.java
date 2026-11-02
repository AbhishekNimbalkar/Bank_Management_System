package com.bankmanagement.incentive.client;

import com.bankmanagement.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "organization-service")
public interface OrganizationClient {

    @GetMapping("/api/v1/platform/my-entitlements")
    ApiResponse<Entitlements> entitlements();

    @GetMapping("/api/v1/employees/{id}")
    ApiResponse<Employee> employee(@PathVariable("id") UUID employeeId);

    record Features(boolean geofencing, boolean incentives, boolean multiBranch, boolean auditExport) {
    }

    record Entitlements(boolean active, int maxCustomers, Features features) {
    }

    record Employee(UUID employeeId, UUID userId, String code, String name, String role, String designation,
                    UUID branchId, String branchName, String status) {
    }
}

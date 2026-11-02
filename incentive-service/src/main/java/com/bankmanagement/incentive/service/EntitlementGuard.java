package com.bankmanagement.incentive.service;

import com.bankmanagement.common.exception.BusinessException;
import com.bankmanagement.incentive.client.OrganizationClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Checks the organization's plan: incentives feature enabled and organization active. */
@Component
@RequiredArgsConstructor
public class EntitlementGuard {

    private final OrganizationClient organizationClient;

    public OrganizationClient.Entitlements requireIncentives() {
        OrganizationClient.Entitlements e = organizationClient.entitlements().data();
        if (!e.active()) {
            throw BusinessException.forbidden("This organization is suspended.");
        }
        if (e.features() == null || !e.features().incentives()) {
            throw BusinessException.forbidden("Incentives are not included in your organization's plan.");
        }
        return e;
    }
}

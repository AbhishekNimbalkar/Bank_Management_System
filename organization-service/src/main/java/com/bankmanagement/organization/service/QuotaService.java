package com.bankmanagement.organization.service;

import com.bankmanagement.common.exception.BusinessException;
import com.bankmanagement.common.exception.ErrorCode;
import com.bankmanagement.organization.domain.RecordStatus;
import com.bankmanagement.organization.domain.Tenant;
import com.bankmanagement.organization.dto.TenantDto;
import com.bankmanagement.organization.repository.BankRepository;
import com.bankmanagement.organization.repository.BranchRepository;
import com.bankmanagement.organization.repository.EmployeeRepository;
import com.bankmanagement.organization.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Enforces the resource ceilings the platform owner set for each organization. */
@Service
@RequiredArgsConstructor
public class QuotaService {

    private final TenantRepository tenants;
    private final BankRepository banks;
    private final BranchRepository branches;
    private final EmployeeRepository employees;

    @Transactional(readOnly = true)
    public TenantDto.Usage usage(String tenantCode) {
        return new TenantDto.Usage(banks.countByTenantCode(tenantCode), branches.countByTenantCode(tenantCode),
                employees.countByTenantCodeAndStatus(tenantCode, RecordStatus.ACTIVE));
    }

    @Transactional(readOnly = true)
    public TenantDto.Quota quota(String tenantCode) {
        Tenant t = tenant(tenantCode);
        TenantDto.Usage u = usage(tenantCode);
        return new TenantDto.Quota(
                new TenantDto.Limits(t.getMaxBanks(), t.getMaxBranches(), t.getMaxEmployees(), t.getMaxCustomers()),
                u, u.banks() < t.getMaxBanks(), u.branches() < t.getMaxBranches(),
                u.activeEmployees() < t.getMaxEmployees());
    }

    /** Limits and feature flags other services enforce (e.g. customer quota in incentive-service). */
    @Transactional(readOnly = true)
    public TenantDto.Entitlements entitlements(String tenantCode) {
        Tenant t = tenants.findByCode(tenantCode).orElseThrow(() -> BusinessException.notFound("Organization"));
        return new TenantDto.Entitlements(t.getStatus() == Tenant.Status.ACTIVE, t.getMaxCustomers(),
                new TenantDto.Features(t.isGeofencing(), t.isIncentives(), t.isMultiBranch(), t.isAuditExport()));
    }

    /* The assert* methods must run inside the caller's write transaction: they take a row lock. */

    public void assertCanAddBank(String tenantCode) {
        Tenant t = locked(tenantCode);
        long used = banks.countByTenantCode(tenantCode);
        if (used >= t.getMaxBanks()) {
            throw exceeded("Bank", used, t.getMaxBanks());
        }
    }

    public void assertCanAddBranch(String tenantCode) {
        Tenant t = locked(tenantCode);
        long used = branches.countByTenantCode(tenantCode);
        if (!t.isMultiBranch() && used >= 1) {
            throw new BusinessException(ErrorCode.QUOTA_EXCEEDED, "Your plan does not include multiple branches.");
        }
        if (used >= t.getMaxBranches()) {
            throw exceeded("Branch", used, t.getMaxBranches());
        }
    }

    public void assertCanAddEmployee(String tenantCode) {
        Tenant t = locked(tenantCode);
        long used = employees.countByTenantCodeAndStatus(tenantCode, RecordStatus.ACTIVE);
        if (used >= t.getMaxEmployees()) {
            throw exceeded("Employee", used, t.getMaxEmployees());
        }
    }

    private Tenant tenant(String tenantCode) {
        return active(tenants.findByCode(tenantCode).orElseThrow(() -> BusinessException.notFound("Organization")));
    }

    private Tenant locked(String tenantCode) {
        return active(tenants.lockByCode(tenantCode).orElseThrow(() -> BusinessException.notFound("Organization")));
    }

    private static Tenant active(Tenant t) {
        if (t.getStatus() != Tenant.Status.ACTIVE) {
            throw BusinessException.forbidden("This organization is suspended.");
        }
        return t;
    }

    private static BusinessException exceeded(String what, long used, int max) {
        return new BusinessException(ErrorCode.QUOTA_EXCEEDED, what + " limit reached (" + used + "/" + max
                + "). Contact the platform owner to expand your allocation.");
    }
}

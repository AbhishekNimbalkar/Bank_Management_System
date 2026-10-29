package com.bankmanagement.auth.service;

import com.bankmanagement.auth.domain.TenantAccess;
import com.bankmanagement.auth.repository.TenantAccessRepository;
import com.bankmanagement.common.audit.AuditPublisher;
import com.bankmanagement.common.exception.BusinessException;
import com.bankmanagement.common.security.Role;
import com.bankmanagement.common.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TenantAccessService {

    private final TenantAccessRepository repository;
    private final AuditPublisher audit;

    @Transactional(readOnly = true)
    public boolean isActive(String tenantCode) {
        if (Role.PLATFORM_TENANT.equals(tenantCode)) {
            return true;
        }
        return repository.findById(tenantCode)
                .map(t -> t.getStatus() == TenantAccess.Status.ACTIVE)
                .orElse(false);
    }

    @Transactional
    public void setStatus(String tenantCode, TenantAccess.Status status) {
        var actor = SecurityUtils.currentUser();
        if (!actor.isPlatformOwner()) {
            throw BusinessException.forbidden("Only the platform owner can change organization access.");
        }
        if (!SecurityUtils.isValidTenantCode(tenantCode) || Role.PLATFORM_TENANT.equals(tenantCode)) {
            throw BusinessException.validation("Invalid organization code.", "tenantCode");
        }
        TenantAccess access = repository.findById(tenantCode)
                .orElseGet(() -> new TenantAccess(tenantCode, status, Instant.now()));
        access.setStatus(status);
        access.setUpdatedAt(Instant.now());
        repository.save(access);
        audit.record("superadmins", "Organization access " + status.name().toLowerCase(), tenantCode);
    }

    /** Sign-in and token refresh both stop as soon as a tenant is suspended. */
    public void assertActive(String tenantCode) {
        if (!isActive(tenantCode)) {
            throw BusinessException.forbidden("Your organization's access is suspended. Contact the platform owner.");
        }
    }
}

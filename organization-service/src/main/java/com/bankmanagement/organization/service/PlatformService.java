package com.bankmanagement.organization.service;

import com.bankmanagement.common.audit.AuditPublisher;
import com.bankmanagement.common.exception.BusinessException;
import com.bankmanagement.common.security.SecurityUtils;
import com.bankmanagement.organization.client.AuthClient;
import com.bankmanagement.organization.domain.Bank;
import com.bankmanagement.organization.domain.RecordStatus;
import com.bankmanagement.organization.domain.Tenant;
import com.bankmanagement.organization.dto.PlatformDto;
import com.bankmanagement.organization.repository.BankRepository;
import com.bankmanagement.organization.repository.BranchRepository;
import com.bankmanagement.organization.repository.EmployeeRepository;
import com.bankmanagement.organization.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Platform-owner (SSA) oversight across all organizations. Every method re-checks the caller is the
 * platform owner, independently of the controller annotations.
 */
@Service
@RequiredArgsConstructor
public class PlatformService {

    private final TenantRepository tenants;
    private final BankRepository banks;
    private final BranchRepository branches;
    private final EmployeeRepository employees;
    private final AuthClient authClient;
    private final AuditPublisher audit;

    @Transactional(readOnly = true)
    public PlatformDto.Stats stats() {
        requirePlatformOwner();
        return new PlatformDto.Stats(tenants.count(), tenants.countByStatus(Tenant.Status.ACTIVE),
                tenants.countByStatus(Tenant.Status.SUSPENDED), banks.count(), branches.count(),
                employees.countByStatus(RecordStatus.ACTIVE));
    }

    @Transactional(readOnly = true)
    public List<PlatformDto.BankAllocation> banks(String tenantCode) {
        requirePlatformOwner();
        Map<String, Tenant> byCode = tenantsByCode();
        Map<UUID, Long> branchCounts = toMap(branches.countPerBank());
        Map<UUID, Long> staffCounts = toMap(employees.countPerBank(RecordStatus.ACTIVE));
        return banks.findAll(Sort.by("tenantCode", "name")).stream()
                .filter(b -> tenantCode == null || tenantCode.equals(b.getTenantCode()))
                .map(b -> {
                    Tenant t = byCode.get(b.getTenantCode());
                    return new PlatformDto.BankAllocation(b.getId(), b.getTenantCode(),
                            t == null ? null : t.getOrganizationName(), b.getName(), b.getCode(),
                            b.getLocation().getCity(), b.getLocation().getState(), b.getStatus().name(),
                            branchCounts.getOrDefault(b.getId(), 0L), t == null ? 0 : t.getMaxBranches(),
                            staffCounts.getOrDefault(b.getId(), 0L));
                }).toList();
    }

    @Transactional(readOnly = true)
    public List<PlatformDto.BranchAllocation> branches(String tenantCode) {
        requirePlatformOwner();
        Map<String, Tenant> byCode = tenantsByCode();
        Map<UUID, String> bankNames = banks.findAll().stream()
                .collect(Collectors.toMap(Bank::getId, Bank::getName));
        Map<UUID, Long> staffCounts = toMap(employees.countPerBranch(RecordStatus.ACTIVE));
        return branches.findAll(Sort.by("tenantCode", "name")).stream()
                .filter(b -> tenantCode == null || tenantCode.equals(b.getTenantCode()))
                .map(b -> {
                    Tenant t = byCode.get(b.getTenantCode());
                    return new PlatformDto.BranchAllocation(b.getId(), b.getTenantCode(),
                            t == null ? null : t.getOrganizationName(), b.getBankId(), bankNames.get(b.getBankId()),
                            b.getName(), b.getIfsc(), b.getLocation().getCity(), b.getLocation().getLatitude(),
                            b.getLocation().getLongitude(), b.getRadiusMeters(), b.isHeadquarters(),
                            b.getStatus().name(), staffCounts.getOrDefault(b.getId(), 0L));
                }).toList();
    }

    /** Issues a new one-time password for an organization's Super Admin (e.g. lost credentials). */
    @Transactional(readOnly = true)
    public PlatformDto.AdminPasswordReset resetAdminPassword(String code) {
        requirePlatformOwner();
        Tenant t = tenant(code);
        if (t.getStatus() == Tenant.Status.ARCHIVED) {
            throw BusinessException.rule("This organization is archived.");
        }
        AuthClient.TemporaryPassword result = authClient.resetPasswordInTenant(code, adminUserId(t)).data();
        audit.record("superadmins", "Reset organization admin password", t.getOrganizationName() + " (" + code + ")");
        return new PlatformDto.AdminPasswordReset(code, result.username(), result.temporaryPassword());
    }

    /** Short-lived, audited support session as the organization's Super Admin (no refresh token). */
    @Transactional(readOnly = true)
    public Object supportSession(String code) {
        requirePlatformOwner();
        Tenant t = tenant(code);
        if (t.getStatus() != Tenant.Status.ACTIVE) {
            throw BusinessException.rule("Support sessions are not available for a suspended organization.");
        }
        return authClient.impersonate(adminUserId(t)).data();
    }

    private Tenant tenant(String code) {
        return tenants.findByCode(code).orElseThrow(() -> BusinessException.notFound("Organization"));
    }

    private static UUID adminUserId(Tenant t) {
        if (t.getAdminUserId() == null) {
            throw BusinessException.rule("This organization has no admin login.");
        }
        return t.getAdminUserId();
    }

    private Map<String, Tenant> tenantsByCode() {
        return tenants.findAll().stream().collect(Collectors.toMap(Tenant::getCode, Function.identity()));
    }

    private static Map<UUID, Long> toMap(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(r -> (UUID) r[0], r -> (Long) r[1]));
    }

    private static void requirePlatformOwner() {
        if (!SecurityUtils.currentUser().isPlatformOwner()) {
            throw BusinessException.forbidden("Only the platform owner can access platform-wide data.");
        }
    }
}

package com.bankmanagement.organization.service;

import com.bankmanagement.common.audit.AuditPublisher;
import com.bankmanagement.common.exception.BusinessException;
import com.bankmanagement.common.security.AuthenticatedUser;
import com.bankmanagement.common.security.Role;
import com.bankmanagement.common.security.SecurityUtils;
import com.bankmanagement.organization.client.AuthClient;
import com.bankmanagement.organization.domain.Bank;
import com.bankmanagement.organization.domain.Branch;
import com.bankmanagement.organization.domain.Employee;
import com.bankmanagement.organization.domain.RecordStatus;
import com.bankmanagement.organization.domain.Tenant;
import com.bankmanagement.organization.dto.TenantDto;
import com.bankmanagement.organization.repository.BranchRepository;
import com.bankmanagement.organization.repository.EmployeeRepository;
import com.bankmanagement.organization.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Platform-owner operations: provisioning organizations and governing their quotas. */
@Service
@RequiredArgsConstructor
public class TenantService {

    private static final Set<String> RESERVED_CODES = Set.of(
            Role.PLATFORM_TENANT, "admin", "api", "master", "www", "root", "system", "null", "undefined");

    private final TenantRepository tenants;
    private final BranchRepository branches;
    private final EmployeeRepository employees;
    private final BankService bankService;
    private final QuotaService quotaService;
    private final AuthClient authClient;
    private final AuditPublisher audit;

    @Transactional(readOnly = true)
    public List<TenantDto.Response> list() {
        requirePlatformOwner();
        return tenants.findAll().stream()
                .map(t -> TenantDto.Response.from(t, quotaService.usage(t.getCode())))
                .toList();
    }

    @Transactional(readOnly = true)
    public TenantDto.Response get(String code) {
        requirePlatformOwner();
        Tenant t = find(code);
        return TenantDto.Response.from(t, quotaService.usage(code));
    }

    /**
     * Creates the organization, its first bank + headquarters branch and the Super Admin employee, then
     * the admin login in auth-service. Any failure rolls back the local transaction.
     */
    @Transactional
    public TenantDto.Provisioned create(TenantDto.CreateRequest req) {
        requirePlatformOwner();
        String code = req.code().toLowerCase(Locale.ROOT);
        if (RESERVED_CODES.contains(code)) {
            throw BusinessException.validation("That organization code is reserved.", "code");
        }
        if (tenants.existsByCode(code)) {
            throw BusinessException.conflict("Organization code '" + code + "' is already registered.", "code");
        }

        Tenant tenant = new Tenant();
        tenant.setCode(code);
        tenant.setOrganizationName(req.organizationName().trim());
        tenant.setHeadquarters(req.headquarters().city() + ", " + req.headquarters().state());
        tenant.setPlan(req.plan());
        tenant.setStatus(Tenant.Status.ACTIVE);
        applyLimits(tenant, req.limits());
        applyFeatures(tenant, req.features());
        tenant.setAdminName(req.admin().name().trim());
        tenant.setAdminUsername(req.admin().username());
        tenant.setAdminEmail(req.admin().email().toLowerCase(Locale.ROOT));
        tenant.setAdminPhone(req.admin().phone());
        tenants.saveAndFlush(tenant);

        Bank bank = bankService.createWithHeadquarters(code, req.organizationName(), null, req.headquarters());
        Branch hq = branches.findByTenantCodeAndBankIdOrderByNameAsc(code, bank.getId()).get(0);

        Employee admin = new Employee();
        admin.setTenantCode(code);
        admin.setCodeNumber(101);
        admin.setCode(CodeGenerator.employeePrefix(code) + 101);
        admin.setName(tenant.getAdminName());
        admin.setUsername(tenant.getAdminUsername());
        admin.setEmail(tenant.getAdminEmail());
        admin.setPhone(tenant.getAdminPhone());
        admin.setRole(Role.SA.name());
        admin.setDesignation("Super Administrator");
        admin.setBankId(bank.getId());
        admin.setBranchId(hq.getId());
        admin.setAddress(hq.getLocation().getAddress());
        admin.setJoinedOn(LocalDate.now(ZoneId.of("Asia/Kolkata")));
        admin.setStatus(RecordStatus.ACTIVE);
        employees.saveAndFlush(admin);

        authClient.setTenantStatus(code, new AuthClient.StatusCommand("ACTIVE"));
        AuthClient.CreatedUser user = authClient.createUser(new AuthClient.CreateUserCommand(code,
                admin.getUsername(), admin.getName(), admin.getEmail(), Role.SA.name(), admin.getId(), admin.getCode(),
                hq.getId(), req.admin().initialPassword())).data();
        admin.setUserId(user.userId());
        tenant.setAdminUserId(user.userId());

        audit.record("superadmins", "Provisioned organization", tenant.getOrganizationName() + " (" + code + "), "
                + tenant.getMaxBanks() + " banks / " + tenant.getMaxBranches() + " branches");
        return new TenantDto.Provisioned(TenantDto.Response.from(tenant, quotaService.usage(code)),
                user.username(), user.temporaryPassword());
    }

    @Transactional
    public TenantDto.Response update(String code, TenantDto.UpdateRequest req) {
        requirePlatformOwner();
        Tenant tenant = find(code);
        assertNotArchived(tenant);
        if (req.status() == Tenant.Status.ARCHIVED) {
            throw BusinessException.validation("Use the archive action to archive an organization.", "status");
        }
        TenantDto.Usage usage = quotaService.usage(code);
        TenantDto.Limits l = req.limits();
        if (l.maxBanks() < usage.banks() || l.maxBranches() < usage.branches()
                || l.maxEmployees() < usage.activeEmployees()) {
            throw BusinessException.rule("Limits cannot be set below current usage (" + usage.banks() + " banks, "
                    + usage.branches() + " branches, " + usage.activeEmployees() + " active employees).");
        }
        applyLimits(tenant, l);
        applyFeatures(tenant, req.features());
        tenant.setPlan(req.plan());
        if (tenant.getStatus() != req.status()) {
            tenant.setStatus(req.status());
            authClient.setTenantStatus(code, new AuthClient.StatusCommand(
                    req.status() == Tenant.Status.ACTIVE ? "ACTIVE" : "SUSPENDED"));
        }
        audit.record("superadmins", "Updated organization", code + ": " + tenant.getPlan() + ", " + tenant.getStatus()
                + ", limits " + l.maxBanks() + "/" + l.maxBranches() + "/" + l.maxEmployees() + "/" + l.maxCustomers());
        return TenantDto.Response.from(tenant, usage);
    }

    @Transactional
    public TenantDto.Response updateProfile(String code, TenantDto.ProfileRequest req) {
        requirePlatformOwner();
        Tenant tenant = find(code);
        assertNotArchived(tenant);
        tenant.setOrganizationName(req.organizationName().trim());
        tenant.setHeadquarters(req.headquarters().trim());
        tenant.setAdminName(req.adminName().trim());
        tenant.setAdminEmail(req.adminEmail().toLowerCase(Locale.ROOT));
        tenant.setAdminPhone(req.adminPhone());
        audit.record("superadmins", "Updated organization profile", tenant.getOrganizationName() + " (" + code + ")");
        return TenantDto.Response.from(tenant, quotaService.usage(code));
    }

    /**
     * Irreversible: sign-in is blocked for everyone in the organization and no further changes are
     * accepted, but every record is kept for audit and regulatory retention.
     */
    @Transactional
    public TenantDto.Response archive(String code, TenantDto.ArchiveRequest req) {
        requirePlatformOwner();
        Tenant tenant = find(code);
        assertNotArchived(tenant);
        if (!code.equals(req.confirmCode())) {
            throw BusinessException.validation("Type the organization code exactly to confirm archiving.", "confirmCode");
        }
        tenant.setStatus(Tenant.Status.ARCHIVED);
        tenant.setArchivedAt(Instant.now());
        authClient.setTenantStatus(code, new AuthClient.StatusCommand("SUSPENDED"));
        audit.record("superadmins", "Archived organization", tenant.getOrganizationName() + " (" + code + ") - "
                + req.reason());
        return TenantDto.Response.from(tenant, quotaService.usage(code));
    }

    private static void assertNotArchived(Tenant tenant) {
        if (tenant.getStatus() == Tenant.Status.ARCHIVED) {
            throw BusinessException.rule("This organization is archived and can no longer be changed.");
        }
    }

    /** Quotas of the caller's own organization (shown on the branches page). */
    @Transactional(readOnly = true)
    public TenantDto.Quota myQuota() {
        return quotaService.quota(SecurityUtils.currentTenant());
    }

    @Transactional(readOnly = true)
    public TenantDto.Entitlements myEntitlements() {
        return quotaService.entitlements(SecurityUtils.currentTenant());
    }

    private Tenant find(String code) {
        return tenants.findByCode(code).orElseThrow(() -> BusinessException.notFound("Organization"));
    }

    private static void requirePlatformOwner() {
        AuthenticatedUser actor = SecurityUtils.currentUser();
        if (!actor.isPlatformOwner()) {
            throw BusinessException.forbidden("Only the platform owner can manage organizations.");
        }
    }

    private static void applyLimits(Tenant t, TenantDto.Limits l) {
        t.setMaxBanks(l.maxBanks());
        t.setMaxBranches(l.maxBranches());
        t.setMaxEmployees(l.maxEmployees());
        t.setMaxCustomers(l.maxCustomers());
    }

    private static void applyFeatures(Tenant t, TenantDto.Features f) {
        t.setGeofencing(f.geofencing());
        t.setIncentives(f.incentives());
        t.setMultiBranch(f.multiBranch());
        t.setAuditExport(f.auditExport());
    }
}

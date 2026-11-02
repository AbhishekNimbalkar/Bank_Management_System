package com.bankmanagement.organization.service;

import com.bankmanagement.common.audit.AuditPublisher;
import com.bankmanagement.common.exception.BusinessException;
import com.bankmanagement.common.security.AuthenticatedUser;
import com.bankmanagement.common.security.SecurityUtils;
import com.bankmanagement.organization.domain.Announcement;
import com.bankmanagement.organization.domain.Bank;
import com.bankmanagement.organization.domain.Branch;
import com.bankmanagement.organization.domain.RecordStatus;
import com.bankmanagement.organization.dto.BranchDto;
import com.bankmanagement.organization.repository.AnnouncementRepository;
import com.bankmanagement.organization.repository.BankRepository;
import com.bankmanagement.organization.repository.BranchRepository;
import com.bankmanagement.organization.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchRepository branches;
    private final BankRepository banks;
    private final EmployeeRepository employees;
    private final AnnouncementRepository announcements;
    private final QuotaService quotaService;
    private final AuditPublisher audit;

    @Transactional(readOnly = true)
    public List<BranchDto.Response> list(UUID bankId) {
        String tenant = SecurityUtils.currentTenant();
        Map<UUID, String> bankNames = banks.findByTenantCodeOrderByNameAsc(tenant).stream()
                .collect(Collectors.toMap(Bank::getId, Bank::getName, (a, b) -> a));
        List<Branch> rows = bankId == null
                ? branches.findByTenantCodeOrderByNameAsc(tenant)
                : branches.findByTenantCodeAndBankIdOrderByNameAsc(tenant, bankId);
        return rows.stream().map(b -> BranchDto.Response.from(b, bankNames.get(b.getBankId()),
                employees.countByTenantCodeAndBranchId(tenant, b.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public BranchDto.Response get(UUID id) {
        String tenant = SecurityUtils.currentTenant();
        Branch branch = find(tenant, id);
        return toResponse(tenant, branch);
    }

    /** Any user may read their own branch geofence (needed to clock in); others need branches:R. */
    @Transactional(readOnly = true)
    public BranchDto.Geofence geofence(UUID id) {
        AuthenticatedUser user = SecurityUtils.currentUser();
        if (!id.equals(user.branchId()) && !user.hasPermission("branches", 'R')) {
            throw BusinessException.forbidden("You can only view your own branch.");
        }
        return BranchDto.Geofence.from(find(SecurityUtils.currentTenant(), id));
    }

    @Transactional
    public BranchDto.Response create(BranchDto.Request req) {
        String tenant = SecurityUtils.currentTenant();
        quotaService.assertCanAddBranch(tenant);
        Bank bank = banks.findByIdAndTenantCode(req.bankId(), tenant)
                .orElseThrow(() -> BusinessException.validation("Select a valid bank.", "bankId"));
        if (branches.existsByTenantCodeAndIfsc(tenant, req.ifsc())) {
            throw BusinessException.conflict("IFSC " + req.ifsc() + " is already registered.", "ifsc");
        }
        Branch branch = new Branch();
        branch.setTenantCode(tenant);
        branch.setBankId(bank.getId());
        apply(branch, req);
        branches.save(branch);
        audit.record("branches", "Created branch", branch.getName() + " (" + branch.getIfsc() + ")");
        return toResponse(tenant, branch);
    }

    @Transactional
    public BranchDto.Response update(UUID id, BranchDto.Request req) {
        String tenant = SecurityUtils.currentTenant();
        Branch branch = find(tenant, id);
        if (branches.existsByTenantCodeAndIfscAndIdNot(tenant, req.ifsc(), id)) {
            throw BusinessException.conflict("IFSC " + req.ifsc() + " is already registered.", "ifsc");
        }
        if (!branch.getBankId().equals(req.bankId())) {
            banks.findByIdAndTenantCode(req.bankId(), tenant)
                    .orElseThrow(() -> BusinessException.validation("Select a valid bank.", "bankId"));
            branch.setBankId(req.bankId());
        }
        apply(branch, req);
        audit.record("branches", "Updated branch", branch.getName() + " (" + branch.getIfsc() + "), radius "
                + branch.getRadiusMeters() + "m");
        return toResponse(tenant, branch);
    }

    @Transactional
    public void delete(UUID id) {
        String tenant = SecurityUtils.currentTenant();
        Branch branch = find(tenant, id);
        if (branch.isHeadquarters()) {
            throw BusinessException.rule("The headquarters branch cannot be deleted.");
        }
        long staff = employees.countByTenantCodeAndBranchId(tenant, id);
        if (staff > 0) {
            throw BusinessException.rule("Cannot delete '" + branch.getName() + "': " + staff
                    + " employee(s) are assigned to it. Reassign them first.");
        }
        branch.markDeleted();
        // The FK used to cascade-delete this branch's announcements; keep that behaviour.
        announcements.findByTenantCodeAndBranchId(tenant, id).forEach(Announcement::markDeleted);
        audit.record("branches", "Deleted branch", branch.getName() + " (" + branch.getIfsc() + ")");
    }

    Branch find(String tenant, UUID id) {
        return branches.findByIdAndTenantCode(id, tenant).orElseThrow(() -> BusinessException.notFound("Branch"));
    }

    private void apply(Branch branch, BranchDto.Request req) {
        branch.setName(req.name().trim());
        branch.setIfsc(req.ifsc());
        req.location().applyTo(branch.getLocation());
        branch.setRadiusMeters(req.radiusMeters());
        branch.setStatus(req.status() == null ? RecordStatus.ACTIVE : req.status());
    }

    private BranchDto.Response toResponse(String tenant, Branch branch) {
        String bankName = banks.findByIdAndTenantCode(branch.getBankId(), tenant).map(Bank::getName).orElse(null);
        return BranchDto.Response.from(branch, bankName, employees.countByTenantCodeAndBranchId(tenant, branch.getId()));
    }

    Map<UUID, Branch> byId(String tenant) {
        return branches.findByTenantCodeOrderByNameAsc(tenant).stream()
                .collect(Collectors.toMap(Branch::getId, Function.identity()));
    }
}

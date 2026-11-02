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

    /** Adds a branch to an already registered bank. The bank's first branch becomes its headquarters. */
    @Transactional
    public BranchDto.Response create(BranchDto.Request req) {
        String tenant = SecurityUtils.currentTenant();
        quotaService.assertCanAddBranch(tenant);
        Bank bank = activeBank(tenant, req.bankId());
        if (branches.existsByTenantCodeAndIfsc(tenant, req.ifsc())) {
            throw BusinessException.conflict("IFSC " + req.ifsc() + " is already registered.", "ifsc");
        }
        boolean firstOfBank = branches.countByTenantCodeAndBankId(tenant, bank.getId()) == 0;
        boolean headquarters = firstOfBank || Boolean.TRUE.equals(req.headquarters());
        if (headquarters) {
            clearHeadquarters(tenant, bank.getId(), null);
        }
        Branch branch = new Branch();
        branch.setTenantCode(tenant);
        branch.setBankId(bank.getId());
        branch.setHeadquarters(headquarters);
        apply(branch, req);
        branches.save(branch);
        audit.record("branches", "Created branch", branch.getName() + " (" + branch.getIfsc() + ") under "
                + bank.getName() + (headquarters ? " as headquarters" : ""));
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
            if (branch.isHeadquarters() && branches.countByTenantCodeAndBankId(tenant, branch.getBankId()) > 1) {
                throw BusinessException.rule("'" + branch.getName() + "' is its bank's headquarters. "
                        + "Mark another branch of that bank as headquarters before moving it.");
            }
            Bank target = activeBank(tenant, req.bankId());
            branch.setBankId(target.getId());
            // Becomes the target's headquarters only if that bank has none yet (or it is requested below).
            branch.setHeadquarters(branches.findByTenantCodeAndBankIdAndHeadquartersTrue(tenant, target.getId())
                    .isEmpty());
        }
        if (Boolean.TRUE.equals(req.headquarters()) && !branch.isHeadquarters()) {
            clearHeadquarters(tenant, branch.getBankId(), branch.getId());
            branch.setHeadquarters(true);
        } else if (Boolean.FALSE.equals(req.headquarters()) && branch.isHeadquarters()) {
            throw BusinessException.rule("A bank must keep a headquarters. Mark another branch as headquarters instead.");
        }
        apply(branch, req);
        audit.record("branches", "Updated branch", branch.getName() + " (" + branch.getIfsc() + "), radius "
                + branch.getRadiusMeters() + "m" + (branch.isHeadquarters() ? ", headquarters" : ""));
        return toResponse(tenant, branch);
    }

    @Transactional
    public void delete(UUID id) {
        String tenant = SecurityUtils.currentTenant();
        Branch branch = find(tenant, id);
        // The headquarters may only go when it is the bank's last branch (so the bank itself can be deleted).
        if (branch.isHeadquarters() && branches.countByTenantCodeAndBankId(tenant, branch.getBankId()) > 1) {
            throw BusinessException.rule("'" + branch.getName() + "' is the headquarters branch. "
                    + "Mark another branch as headquarters before deleting it.");
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

    private Bank activeBank(String tenant, UUID bankId) {
        Bank bank = banks.findByIdAndTenantCode(bankId, tenant)
                .orElseThrow(() -> BusinessException.validation("Select a valid bank.", "bankId"));
        if (bank.getStatus() != RecordStatus.ACTIVE) {
            throw BusinessException.validation("Bank " + bank.getName() + " is inactive; activate it before adding "
                    + "branches to it.", "bankId");
        }
        return bank;
    }

    /** Unmarks the bank's current headquarters (other than {@code keepId}); flushed before the new one is set. */
    private void clearHeadquarters(String tenant, UUID bankId, UUID keepId) {
        branches.findByTenantCodeAndBankIdAndHeadquartersTrue(tenant, bankId)
                .filter(hq -> !hq.getId().equals(keepId))
                .ifPresent(hq -> {
                    hq.setHeadquarters(false);
                    branches.saveAndFlush(hq);
                });
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

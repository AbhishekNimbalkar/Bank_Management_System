package com.bankmanagement.organization.service;

import com.bankmanagement.common.audit.AuditPublisher;
import com.bankmanagement.common.exception.BusinessException;
import com.bankmanagement.common.security.SecurityUtils;
import com.bankmanagement.organization.domain.Bank;
import com.bankmanagement.organization.domain.RecordStatus;
import com.bankmanagement.organization.dto.BankDto;
import com.bankmanagement.organization.repository.BankLogoRepository;
import com.bankmanagement.organization.repository.BankRepository;
import com.bankmanagement.organization.repository.BranchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BankService {

    private final BankRepository banks;
    private final BranchRepository branches;
    private final BankLogoRepository logos;
    private final QuotaService quotaService;
    private final AuditPublisher audit;

    @Transactional(readOnly = true)
    public List<BankDto.Response> list() {
        String tenant = SecurityUtils.currentTenant();
        Set<UUID> withLogo = logos.bankIdsWithLogo(tenant);
        return banks.findByTenantCodeOrderByNameAsc(tenant).stream()
                .map(b -> BankDto.Response.from(b, branches.countByTenantCodeAndBankId(tenant, b.getId()), withLogo.contains(b.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public BankDto.Response get(UUID id) {
        String tenant = SecurityUtils.currentTenant();
        Bank bank = find(tenant, id);
        return BankDto.Response.from(bank, branches.countByTenantCodeAndBankId(tenant, id),
                logos.existsByBankIdAndTenantCode(id, tenant));
    }

    /**
     * Registers the bank only. Branches are added afterwards through the branch API; the first branch
     * added to a bank becomes its headquarters.
     */
    @Transactional
    public BankDto.Response create(BankDto.Request req) {
        String tenant = SecurityUtils.currentTenant();
        quotaService.assertCanAddBank(tenant);

        String code = req.code() != null && !req.code().isBlank() ? req.code()
                : CodeGenerator.bankCode(req.name(), c -> banks.existsByTenantCodeAndCode(tenant, c));
        if (banks.existsByTenantCodeAndCode(tenant, code)) {
            throw BusinessException.conflict("Bank code " + code + " is already used.", "code");
        }

        Bank bank = new Bank();
        bank.setTenantCode(tenant);
        bank.setName(req.name().trim());
        bank.setCode(code);
        req.location().applyTo(bank.getLocation());
        bank.setStatus(req.status() == null ? RecordStatus.ACTIVE : req.status());
        banks.save(bank);
        audit.record("banks", "Created bank", bank.getName() + " (" + bank.getCode() + ")");
        return BankDto.Response.from(bank, 0, false);
    }

    @Transactional
    public BankDto.Response update(UUID id, BankDto.Request req) {
        String tenant = SecurityUtils.currentTenant();
        Bank bank = find(tenant, id);
        bank.setName(req.name().trim());
        req.location().applyTo(bank.getLocation());
        if (req.status() != null) {
            bank.setStatus(req.status());
        }
        audit.record("banks", "Updated bank", bank.getName() + " (" + bank.getCode() + ")");
        return BankDto.Response.from(bank, branches.countByTenantCodeAndBankId(tenant, id),
                logos.existsByBankIdAndTenantCode(id, tenant));
    }

    @Transactional
    public void delete(UUID id) {
        String tenant = SecurityUtils.currentTenant();
        Bank bank = find(tenant, id);
        long branchCount = branches.countByTenantCodeAndBankId(tenant, id);
        if (branchCount > 0) {
            throw BusinessException.rule("Cannot delete bank: it still has " + branchCount
                    + " branch(es). Reassign or delete them first.");
        }
        bank.markDeleted();
        audit.record("banks", "Deleted bank", bank.getName() + " (" + bank.getCode() + ")");
    }

    Bank find(String tenant, UUID id) {
        return banks.findByIdAndTenantCode(id, tenant).orElseThrow(() -> BusinessException.notFound("Bank"));
    }
}

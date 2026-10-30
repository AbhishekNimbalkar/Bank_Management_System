package com.bankmanagement.organization.service;

import com.bankmanagement.common.audit.AuditPublisher;
import com.bankmanagement.common.exception.BusinessException;
import com.bankmanagement.common.security.SecurityUtils;
import com.bankmanagement.organization.domain.Bank;
import com.bankmanagement.organization.domain.Branch;
import com.bankmanagement.organization.domain.RecordStatus;
import com.bankmanagement.organization.dto.BankDto;
import com.bankmanagement.organization.dto.LocationDto;
import com.bankmanagement.organization.repository.BankRepository;
import com.bankmanagement.organization.repository.BranchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BankService {

    static final int HQ_RADIUS_METERS = 200;

    private final BankRepository banks;
    private final BranchRepository branches;
    private final QuotaService quotaService;
    private final AuditPublisher audit;

    @Transactional(readOnly = true)
    public List<BankDto.Response> list() {
        String tenant = SecurityUtils.currentTenant();
        return banks.findByTenantCodeOrderByNameAsc(tenant).stream()
                .map(b -> BankDto.Response.from(b, branches.countByTenantCodeAndBankId(tenant, b.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public BankDto.Response get(UUID id) {
        String tenant = SecurityUtils.currentTenant();
        Bank bank = find(tenant, id);
        return BankDto.Response.from(bank, branches.countByTenantCodeAndBankId(tenant, id));
    }

    /** Creates the bank and its headquarters branch at the registered address. */
    @Transactional
    public BankDto.Response create(BankDto.Request req) {
        String tenant = SecurityUtils.currentTenant();
        Bank bank = createWithHeadquarters(tenant, req.name(), req.code(), req.location());
        audit.record("banks", "Created bank", bank.getName() + " (" + bank.getCode() + ") with headquarters branch");
        return BankDto.Response.from(bank, 1);
    }

    /** Also used when a new organization is provisioned. */
    Bank createWithHeadquarters(String tenant, String name, String requestedCode, LocationDto location) {
        quotaService.assertCanAddBank(tenant);
        quotaService.assertCanAddBranch(tenant);

        String code = requestedCode != null && !requestedCode.isBlank() ? requestedCode
                : CodeGenerator.bankCode(name, c -> banks.existsByTenantCodeAndCode(tenant, c));
        if (banks.existsByTenantCodeAndCode(tenant, code)) {
            throw BusinessException.conflict("Bank code " + code + " is already used.", "code");
        }

        Bank bank = new Bank();
        bank.setTenantCode(tenant);
        bank.setName(name.trim());
        bank.setCode(code);
        location.applyTo(bank.getLocation());
        bank.setStatus(RecordStatus.ACTIVE);
        banks.save(bank);

        Branch hq = new Branch();
        hq.setTenantCode(tenant);
        hq.setBankId(bank.getId());
        hq.setName(location.city().trim() + " Headquarters");
        hq.setIfsc(CodeGenerator.ifsc(code, i -> branches.existsByTenantCodeAndIfsc(tenant, i)));
        location.applyTo(hq.getLocation());
        hq.setRadiusMeters(HQ_RADIUS_METERS);
        hq.setHeadquarters(true);
        hq.setStatus(RecordStatus.ACTIVE);
        branches.save(hq);
        return bank;
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
        return BankDto.Response.from(bank, branches.countByTenantCodeAndBankId(tenant, id));
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

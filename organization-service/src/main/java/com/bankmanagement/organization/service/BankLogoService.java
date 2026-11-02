package com.bankmanagement.organization.service;

import com.bankmanagement.common.audit.AuditPublisher;
import com.bankmanagement.common.exception.BusinessException;
import com.bankmanagement.common.security.SecurityUtils;
import com.bankmanagement.common.web.UploadedFiles;
import com.bankmanagement.organization.domain.Bank;
import com.bankmanagement.organization.domain.BankLogo;
import com.bankmanagement.organization.dto.BankDto;
import com.bankmanagement.organization.repository.BankLogoRepository;
import com.bankmanagement.organization.repository.BranchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Bank logos: PNG, JPEG or WEBP up to 512 KB, validated by content. */
@Service
@RequiredArgsConstructor
public class BankLogoService {

    static final int MAX_BYTES = 512 * 1024;
    private static final Set<String> TYPES = Set.of(UploadedFiles.PNG, UploadedFiles.JPEG, UploadedFiles.WEBP);

    private final BankLogoRepository logos;
    private final BankService bankService;
    private final BranchRepository branches;
    private final AuditPublisher audit;

    public record Image(String contentType, byte[] data) {
    }

    @Transactional
    public BankDto.Response upload(UUID bankId, MultipartFile file) {
        String tenant = SecurityUtils.currentTenant();
        Bank bank = bankService.find(tenant, bankId);
        UploadedFiles.Upload upload = UploadedFiles.read(file, TYPES, MAX_BYTES, "file");

        BankLogo logo = logos.findByBankIdAndTenantCode(bankId, tenant).orElseGet(() -> {
            BankLogo created = new BankLogo();
            created.setBankId(bankId);
            created.setTenantCode(tenant);
            return created;
        });
        logo.setContentType(upload.contentType());
        logo.setData(upload.data());
        logo.setSizeBytes(upload.data().length);
        logo.setUpdatedAt(Instant.now());
        logo.setUpdatedBy(SecurityUtils.currentUser().userId().toString());
        logos.save(logo);

        audit.record("banks", "Updated bank logo", bank.getName() + " (" + upload.contentType() + ", "
                + upload.data().length / 1024 + " KB)");
        return BankDto.Response.from(bank, branches.countByTenantCodeAndBankId(tenant, bankId), true);
    }

    @Transactional(readOnly = true)
    public Image get(UUID bankId) {
        String tenant = SecurityUtils.currentTenant();
        bankService.find(tenant, bankId);
        BankLogo logo = logos.findByBankIdAndTenantCode(bankId, tenant)
                .orElseThrow(() -> BusinessException.notFound("Logo"));
        return new Image(logo.getContentType(), logo.getData());
    }

    @Transactional
    public void delete(UUID bankId) {
        String tenant = SecurityUtils.currentTenant();
        Bank bank = bankService.find(tenant, bankId);
        BankLogo logo = logos.findByBankIdAndTenantCode(bankId, tenant)
                .orElseThrow(() -> BusinessException.notFound("Logo"));
        logos.delete(logo);
        audit.record("banks", "Removed bank logo", bank.getName());
    }
}

package com.bankmanagement.organization.service;

import com.bankmanagement.common.audit.AuditPublisher;
import com.bankmanagement.common.exception.BusinessException;
import com.bankmanagement.common.security.SecurityUtils;
import com.bankmanagement.organization.domain.Designation;
import com.bankmanagement.organization.domain.RecordStatus;
import com.bankmanagement.organization.dto.ConfigDto;
import com.bankmanagement.organization.repository.DesignationRepository;
import com.bankmanagement.organization.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DesignationService {

    private final DesignationRepository designations;
    private final EmployeeRepository employees;
    private final AuditPublisher audit;

    @Transactional(readOnly = true)
    public List<ConfigDto.DesignationResponse> list() {
        String tenant = SecurityUtils.currentTenant();
        return designations.findByTenantCodeOrderByTitleAsc(tenant).stream()
                .map(d -> ConfigDto.DesignationResponse.from(d, employees.countByTenantCodeAndDesignation(tenant, d.getTitle())))
                .toList();
    }

    @Transactional
    public ConfigDto.DesignationResponse create(ConfigDto.DesignationRequest req) {
        String tenant = SecurityUtils.currentTenant();
        String title = req.title().trim();
        if (designations.existsByTenantCodeAndTitleIgnoreCase(tenant, title)) {
            throw BusinessException.conflict("This designation already exists.", "title");
        }
        Designation d = new Designation();
        d.setTenantCode(tenant);
        apply(d, req, title);
        designations.save(d);
        audit.record("config", "Created designation", title);
        return ConfigDto.DesignationResponse.from(d, 0);
    }

    @Transactional
    public ConfigDto.DesignationResponse update(UUID id, ConfigDto.DesignationRequest req) {
        String tenant = SecurityUtils.currentTenant();
        Designation d = find(tenant, id);
        String title = req.title().trim();
        if (designations.existsByTenantCodeAndTitleIgnoreCaseAndIdNot(tenant, title, id)) {
            throw BusinessException.conflict("This designation already exists.", "title");
        }
        if (!d.getTitle().equals(title) && employees.countByTenantCodeAndDesignation(tenant, d.getTitle()) > 0) {
            throw BusinessException.rule("This designation is assigned to employees; create a new one instead of renaming.");
        }
        apply(d, req, title);
        audit.record("config", "Updated designation", title);
        return ConfigDto.DesignationResponse.from(d, employees.countByTenantCodeAndDesignation(tenant, title));
    }

    @Transactional
    public void delete(UUID id) {
        String tenant = SecurityUtils.currentTenant();
        Designation d = find(tenant, id);
        long inUse = employees.countByTenantCodeAndDesignation(tenant, d.getTitle());
        if (inUse > 0) {
            throw BusinessException.rule("Cannot delete '" + d.getTitle() + "': assigned to " + inUse
                    + " employee(s). Reassign them first.");
        }
        d.markDeleted();
        audit.record("config", "Deleted designation", d.getTitle());
    }

    private static void apply(Designation d, ConfigDto.DesignationRequest req, String title) {
        d.setTitle(title);
        d.setDepartment(req.department());
        d.setGrade(req.grade());
        d.setDescription(req.description());
        d.setStatus(req.status() == null ? RecordStatus.ACTIVE : req.status());
    }

    private Designation find(String tenant, UUID id) {
        return designations.findByIdAndTenantCode(id, tenant).orElseThrow(() -> BusinessException.notFound("Designation"));
    }
}

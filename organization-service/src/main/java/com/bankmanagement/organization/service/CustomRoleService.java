package com.bankmanagement.organization.service;

import com.bankmanagement.common.audit.AuditPublisher;
import com.bankmanagement.common.exception.BusinessException;
import com.bankmanagement.common.security.Role;
import com.bankmanagement.common.security.SecurityUtils;
import com.bankmanagement.organization.domain.CustomRole;
import com.bankmanagement.organization.domain.RecordStatus;
import com.bankmanagement.organization.dto.ConfigDto;
import com.bankmanagement.organization.repository.CustomRoleRepository;
import com.bankmanagement.organization.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Built-in roles are listed read-only; organizations can add their own (e.g. Internal Auditor). */
@Service
@RequiredArgsConstructor
public class CustomRoleService {

    private final CustomRoleRepository roles;
    private final EmployeeRepository employees;
    private final AuditPublisher audit;

    @Transactional(readOnly = true)
    public List<ConfigDto.RoleResponse> list() {
        String tenant = SecurityUtils.currentTenant();
        List<ConfigDto.RoleResponse> result = new ArrayList<>();
        Arrays.stream(Role.values()).filter(r -> r != Role.SSA).forEach(r ->
                result.add(new ConfigDto.RoleResponse(null, r.name(), r.label(), "Built-in", null, true,
                        RecordStatus.ACTIVE, employees.countByTenantCodeAndRole(tenant, r.name()))));
        roles.findByTenantCodeOrderByNameAsc(tenant).forEach(r ->
                result.add(ConfigDto.RoleResponse.from(r, employees.countByTenantCodeAndRole(tenant, r.getCode()))));
        return result;
    }

    @Transactional
    public ConfigDto.RoleResponse create(ConfigDto.RoleRequest req) {
        String tenant = SecurityUtils.currentTenant();
        String base = req.code() != null ? req.code() : initials(req.name());
        String code = base;
        for (int i = 1; Role.isBuiltIn(code) || roles.existsByTenantCodeAndCode(tenant, code); i++) {
            if (req.code() != null) {
                throw BusinessException.conflict("Role code '" + code + "' is reserved or already used.", "code");
            }
            code = base + i;
        }
        CustomRole role = new CustomRole();
        role.setTenantCode(tenant);
        role.setCode(code);
        apply(role, req);
        roles.save(role);
        audit.record("config", "Created role", role.getName() + " [" + code + "]");
        return ConfigDto.RoleResponse.from(role, 0);
    }

    @Transactional
    public ConfigDto.RoleResponse update(UUID id, ConfigDto.RoleRequest req) {
        String tenant = SecurityUtils.currentTenant();
        CustomRole role = find(tenant, id);
        long inUse = employees.countByTenantCodeAndRole(tenant, role.getCode());
        if (req.status() == RecordStatus.INACTIVE && inUse > 0) {
            throw BusinessException.rule("Cannot deactivate a role assigned to " + inUse + " employee(s).");
        }
        apply(role, req);
        audit.record("config", "Updated role", role.getName() + " [" + role.getCode() + "]");
        return ConfigDto.RoleResponse.from(role, inUse);
    }

    @Transactional
    public void delete(UUID id) {
        String tenant = SecurityUtils.currentTenant();
        CustomRole role = find(tenant, id);
        long inUse = employees.countByTenantCodeAndRole(tenant, role.getCode());
        if (inUse > 0) {
            throw BusinessException.rule("Cannot delete role '" + role.getName() + "': assigned to " + inUse
                    + " employee(s). Reassign them first.");
        }
        role.markDeleted();
        audit.record("config", "Deleted role", role.getName() + " [" + role.getCode() + "]");
    }

    private static void apply(CustomRole role, ConfigDto.RoleRequest req) {
        role.setName(req.name().trim());
        role.setCategory(req.category() == null ? "General Operations" : req.category());
        role.setDescription(req.description());
        role.setStatus(req.status() == null ? RecordStatus.ACTIVE : req.status());
    }

    private static String initials(String name) {
        StringBuilder sb = new StringBuilder();
        for (String w : name.trim().split("[^A-Za-z0-9]+")) {
            if (!w.isEmpty()) {
                sb.append(w.charAt(0));
            }
        }
        String code = (sb.length() >= 2 ? sb.toString() : name.replaceAll("[^A-Za-z0-9]", ""))
                .toUpperCase(Locale.ROOT);
        code = code.length() > 6 ? code.substring(0, 6) : code;
        return code.length() < 2 ? "ROLE" : code;
    }

    private CustomRole find(String tenant, UUID id) {
        return roles.findByIdAndTenantCode(id, tenant).orElseThrow(() -> BusinessException.notFound("Role"));
    }
}

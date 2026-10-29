package com.bankmanagement.auth.service;

import com.bankmanagement.auth.domain.RolePermission;
import com.bankmanagement.auth.dto.PermissionUpdateRequest;
import com.bankmanagement.auth.repository.RolePermissionRepository;
import com.bankmanagement.common.audit.AuditPublisher;
import com.bankmanagement.common.exception.BusinessException;
import com.bankmanagement.common.security.AuthenticatedUser;
import com.bankmanagement.common.security.Role;
import com.bankmanagement.common.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private static final List<String> MATRIX_ROLES = List.of("SA", "HR", "BM", "EMP");
    /** Rights that cannot be revoked, so administrators cannot lock themselves out. */
    private static final Map<String, Set<String>> PROTECTED = Map.of("SA", Set.of("permissions", "dashboard"));

    private final RolePermissionRepository repository;
    private final AuditPublisher audit;

    /** Authorities embedded in a user's access token, e.g. {@code leaves:U}. */
    @Transactional(readOnly = true)
    public Set<String> effectivePermissions(String tenantCode, String role) {
        Set<String> result = new LinkedHashSet<>();
        if (Role.SSA.name().equals(role)) {
            DefaultPermissions.MODULES.forEach(m -> DefaultPermissions.ACTIONS.forEach(a -> result.add(m + ":" + a)));
            return result;
        }
        Map<String, String> overrides = new LinkedHashMap<>();
        repository.findByTenantCodeAndRoleCode(tenantCode, role).forEach(p -> overrides.put(p.getModule(), p.getActions()));
        for (String module : DefaultPermissions.MODULES) {
            if (DefaultPermissions.PLATFORM_MODULES.contains(module)) {
                continue;
            }
            String actions = overrides.getOrDefault(module, DefaultPermissions.actions(role, module));
            for (char a : actions.toCharArray()) {
                result.add(module + ":" + a);
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Map<String, String>> matrix() {
        return matrix(List.of());
    }

    /**
     * Matrix for the built-in roles plus the organization's custom roles (codes from Config -> Roles).
     * Custom roles start with no rights until granted here.
     */
    @Transactional(readOnly = true)
    public Map<String, Map<String, String>> matrix(List<String> customRoles) {
        String tenant = SecurityUtils.currentTenant();
        List<String> roles = new ArrayList<>(MATRIX_ROLES);
        customRoles.stream()
                .filter(r -> r != null && r.matches("^[A-Z][A-Z0-9]{1,9}$") && !Role.isBuiltIn(r) && !roles.contains(r))
                .limit(50)
                .forEach(roles::add);
        Map<String, Map<String, String>> overrides = new LinkedHashMap<>();
        repository.findByTenantCode(tenant).forEach(p ->
                overrides.computeIfAbsent(p.getRoleCode(), k -> new LinkedHashMap<>()).put(p.getModule(), p.getActions()));

        overrides.keySet().stream().filter(r -> !roles.contains(r)).forEach(roles::add);
        Map<String, Map<String, String>> matrix = new LinkedHashMap<>();
        for (String role : roles) {
            Map<String, String> row = new LinkedHashMap<>();
            for (String module : DefaultPermissions.MODULES) {
                if (DefaultPermissions.PLATFORM_MODULES.contains(module)) {
                    row.put(module, "");
                    continue;
                }
                row.put(module, overrides.getOrDefault(role, Map.of())
                        .getOrDefault(module, DefaultPermissions.actions(role, module)));
            }
            matrix.put(role, row);
        }
        return matrix;
    }

    @Transactional
    public Map<String, Map<String, String>> update(PermissionUpdateRequest req) {
        AuthenticatedUser actor = SecurityUtils.currentUser();
        String tenant = SecurityUtils.currentTenant();
        requireMatrixAdmin(actor);

        String role = req.role();
        String module = req.module();
        String action = req.action();
        boolean granted = req.granted();

        if (Role.SSA.name().equals(role)) {
            throw BusinessException.validation("The platform owner's rights are fixed.", "role");
        }
        if (!DefaultPermissions.MODULES.contains(module)) {
            throw BusinessException.validation("Unknown module.", "module");
        }
        if (DefaultPermissions.PLATFORM_MODULES.contains(module)) {
            throw BusinessException.validation("Platform modules can never be granted to organization roles.", "module");
        }
        if (granted && !actor.hasPermission(module, action.charAt(0))) {
            throw BusinessException.forbidden("You cannot grant a right you do not hold yourself.");
        }
        if (!granted && PROTECTED.getOrDefault(role, Set.of()).contains(module) && ("R".equals(action) || "U".equals(action))) {
            throw BusinessException.validation("This right is protected so administrators cannot lock themselves out.",
                    "action");
        }

        RolePermission row = repository.findByTenantCodeAndRoleCodeAndModule(tenant, role, module).orElseGet(() -> {
            RolePermission p = new RolePermission();
            p.setTenantCode(tenant);
            p.setRoleCode(role);
            p.setModule(module);
            p.setActions(DefaultPermissions.actions(role, module));
            return p;
        });

        Set<String> actions = new LinkedHashSet<>(List.of(row.getActions().split("")));
        actions.remove("");
        if (granted) {
            actions.add(action);
            actions.add("R"); // any write right implies read
        } else if ("R".equals(action)) {
            actions.clear(); // no read means no access at all
        } else {
            actions.remove(action);
        }
        row.setActions(DefaultPermissions.normalize(String.join("", actions)));
        repository.save(row);

        audit.record("permissions", "Changed permission",
                role + " / " + module + " -> " + (row.getActions().isEmpty() ? "none" : row.getActions()));
        return matrix();
    }

    @Transactional
    public Map<String, Map<String, String>> reset() {
        AuthenticatedUser actor = SecurityUtils.currentUser();
        requireMatrixAdmin(actor);
        repository.deleteByTenantCode(SecurityUtils.currentTenant());
        audit.record("permissions", "Reset permissions to defaults", "");
        return matrix();
    }

    private static void requireMatrixAdmin(AuthenticatedUser actor) {
        boolean admin = actor.isPlatformOwner() || Role.SA.name().equals(actor.role());
        if (!admin || !actor.hasPermission("permissions", 'U')) {
            throw BusinessException.forbidden("Only a Super Admin can change the permission matrix.");
        }
    }
}

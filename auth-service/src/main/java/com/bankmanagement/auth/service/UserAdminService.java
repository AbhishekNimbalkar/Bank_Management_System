package com.bankmanagement.auth.service;

import com.bankmanagement.auth.domain.UserAccount;
import com.bankmanagement.auth.domain.UserStatus;
import com.bankmanagement.auth.dto.CreateUserRequest;
import com.bankmanagement.auth.dto.CreatedUserResponse;
import com.bankmanagement.auth.dto.TemporaryPasswordResponse;
import com.bankmanagement.auth.repository.RefreshTokenRepository;
import com.bankmanagement.auth.repository.UserAccountRepository;
import com.bankmanagement.common.audit.AuditPublisher;
import com.bankmanagement.common.exception.BusinessException;
import com.bankmanagement.common.security.AuthenticatedUser;
import com.bankmanagement.common.security.Role;
import com.bankmanagement.common.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Credential administration. Every operation enforces tenant isolation and the role hierarchy. */
@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final UserAccountRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final PasswordGenerator passwordGenerator;
    private final AuditPublisher audit;

    @Transactional
    public CreatedUserResponse create(CreateUserRequest req) {
        AuthenticatedUser actor = SecurityUtils.currentUser();
        SecurityUtils.requirePermission("employees", 'C');

        String tenant = actor.isPlatformOwner() && req.tenantCode() != null ? req.tenantCode() : SecurityUtils.currentTenant();
        if (Role.PLATFORM_TENANT.equals(tenant)) {
            throw BusinessException.validation("Users must belong to an organization.", "tenantCode");
        }
        assertCanAssignRole(actor, req.role());
        if (Role.BM.name().equals(actor.role()) && (req.branchId() == null || !req.branchId().equals(actor.branchId()))) {
            throw BusinessException.forbidden("Branch Managers can only add staff to their own branch.");
        }

        String username = req.username().toLowerCase(Locale.ROOT);
        if (users.existsByTenantCodeAndUsername(tenant, username)) {
            throw BusinessException.conflict("Username '" + username + "' is already taken.", "username");
        }

        String password = req.password() == null || req.password().isBlank() ? passwordGenerator.generate() : req.password();
        passwordPolicy.validate(password, username, req.displayName());

        UserAccount user = new UserAccount();
        user.setTenantCode(tenant);
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setDisplayName(req.displayName().trim());
        user.setEmail(req.email() == null ? null : req.email().toLowerCase(Locale.ROOT));
        user.setRole(req.role());
        user.setEmployeeId(req.employeeId());
        user.setEmployeeCode(req.employeeCode());
        user.setBranchId(req.branchId());
        user.setStatus(UserStatus.ACTIVE);
        user.setMustChangePassword(true);
        users.save(user);

        audit.publish(tenant, actor.userId().toString(), actor.name(), actor.role(),
                actor.impersonatorId() == null ? null : actor.impersonatorId().toString(),
                "employees", "Created login", username + " (" + req.role() + ")");
        return new CreatedUserResponse(user.getId(), username, password);
    }

    @Transactional
    public TemporaryPasswordResponse resetPassword(UUID userId) {
        UserAccount target = manageable(userId, "employees", 'U');
        String password = passwordGenerator.generate();
        target.setPasswordHash(passwordEncoder.encode(password));
        target.setMustChangePassword(true);
        target.setAdminLocked(false);
        target.clearFailures();
        refreshTokens.revokeAllForUser(target.getId(), Instant.now());
        audit.record("employees", "Reset password", target.getUsername());
        return new TemporaryPasswordResponse(target.getUsername(), password);
    }

    @Transactional
    public void setLocked(UUID userId, boolean locked) {
        UserAccount target = manageable(userId, "employees", 'U');
        target.setAdminLocked(locked);
        if (locked) {
            refreshTokens.revokeAllForUser(target.getId(), Instant.now());
        } else {
            target.clearFailures();
        }
        audit.record("employees", locked ? "Locked account" : "Unlocked account", target.getUsername());
    }

    @Transactional
    public void setStatus(UUID userId, UserStatus status) {
        UserAccount target = manageable(userId, "employees", 'U');
        target.setStatus(status);
        if (status == UserStatus.INACTIVE) {
            refreshTokens.revokeAllForUser(target.getId(), Instant.now());
        }
        audit.record("employees", status == UserStatus.ACTIVE ? "Activated login" : "Deactivated login",
                target.getUsername());
    }

    /**
     * Transfer / promotion. The new role must also be assignable by the caller, and Branch Managers
     * cannot move staff out of their branch. Sessions are revoked so the next sign-in carries the
     * new claims (open access tokens expire within their short TTL).
     */
    @Transactional
    public void updateAssignment(UUID userId, String role, UUID branchId) {
        AuthenticatedUser actor = SecurityUtils.currentUser();
        UserAccount target = manageable(userId, "employees", 'U');
        assertCanAssignRole(actor, role);
        if (Role.BM.name().equals(actor.role()) && !branchId.equals(actor.branchId())) {
            throw BusinessException.forbidden("Branch Managers cannot transfer staff to another branch.");
        }
        String before = target.getRole() + "@" + target.getBranchId();
        target.setRole(role);
        target.setBranchId(branchId);
        refreshTokens.revokeAllForUser(target.getId(), Instant.now());
        audit.record("employees", "Changed login assignment",
                target.getUsername() + ": " + before + " -> " + role + "@" + branchId);
    }

    /** Loads a user the caller may administer: same tenant, in scope, never self, strictly junior. */
    private UserAccount manageable(UUID userId, String module, char action) {
        AuthenticatedUser actor = SecurityUtils.currentUser();
        SecurityUtils.requirePermission(module, action);
        UserAccount target = users.findByIdAndTenantCode(userId, SecurityUtils.currentTenant())
                .orElseThrow(() -> BusinessException.notFound("User"));
        if (target.getId().equals(actor.userId())) {
            throw BusinessException.forbidden("You cannot perform this action on your own account.");
        }
        if (!actor.isPlatformOwner() && Role.rankOf(target.getRole()) >= actor.rank()) {
            throw BusinessException.forbidden("You can only manage users with a lower role than yours.");
        }
        if (Role.BM.name().equals(actor.role()) && !Objects.equals(actor.branchId(), target.getBranchId())) {
            throw BusinessException.forbidden("This user is outside your branch.");
        }
        return target;
    }

    private static void assertCanAssignRole(AuthenticatedUser actor, String role) {
        if (Role.SSA.name().equals(role)) {
            throw BusinessException.forbidden("Platform owner accounts cannot be created through the API.");
        }
        boolean custom = !Role.isBuiltIn(role);
        if (custom && actor.rank() < Role.HR.rank()) {
            throw BusinessException.forbidden("Your role cannot assign custom roles.");
        }
        if (!custom && Role.rankOf(role) >= actor.rank()) {
            throw BusinessException.forbidden("Your role (" + actor.role() + ") cannot create a '" + role + "' user.");
        }
    }
}

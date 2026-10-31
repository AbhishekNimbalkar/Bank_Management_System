package com.bankmanagement.auth.service;

import com.bankmanagement.auth.config.AuthProperties;
import com.bankmanagement.auth.domain.PasswordHistoryEntry;
import com.bankmanagement.auth.domain.RefreshToken;
import com.bankmanagement.auth.domain.UserAccount;
import com.bankmanagement.auth.domain.UserStatus;
import com.bankmanagement.auth.dto.ChangePasswordRequest;
import com.bankmanagement.auth.dto.LoginRequest;
import com.bankmanagement.auth.dto.TokenResponse;
import com.bankmanagement.auth.dto.UserProfile;
import com.bankmanagement.auth.repository.PasswordHistoryRepository;
import com.bankmanagement.auth.repository.RefreshTokenRepository;
import com.bankmanagement.auth.repository.UserAccountRepository;
import com.bankmanagement.common.audit.AuditPublisher;
import com.bankmanagement.common.exception.BusinessException;
import com.bankmanagement.common.exception.ErrorCode;
import com.bankmanagement.common.security.AuthenticatedUser;
import com.bankmanagement.common.security.Role;
import com.bankmanagement.common.security.SecurityUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int PASSWORD_HISTORY = 3;
    private static final String INVALID_CREDENTIALS = "Invalid credentials. Please check your username and password.";

    private final UserAccountRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordHistoryRepository passwordHistory;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final PermissionService permissionService;
    private final TenantAccessService tenantAccess;
    private final TokenService tokenService;
    private final AuthProperties props;
    private final AuditPublisher audit;

    /** Hash used when the username does not exist, so response time does not reveal valid usernames. */
    private String dummyHash;

    @PostConstruct
    void init() {
        dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /** Failure counters must survive the exception, hence no rollback for business errors. */
    @Transactional(noRollbackFor = BusinessException.class)
    public TokenResponse login(LoginRequest req, String ip, String userAgent) {
        String username = req.username().trim().toLowerCase(Locale.ROOT);
        Instant now = Instant.now();

        UserAccount user = users.findByUsername(username).orElse(null);
        String tenant = user != null ? user.getTenantCode() : Role.PLATFORM_TENANT;

        if (user != null && user.isTemporarilyLocked(now)) {
            long minutes = Math.max(1, Duration.between(now, user.getLockedUntil()).toMinutes());
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED,
                    "Too many failed attempts. Try again in " + minutes + " minute(s).");
        }

        boolean passwordOk = passwordEncoder.matches(req.password(), user != null ? user.getPasswordHash() : dummyHash);
        if (user == null || !passwordOk) {
            registerFailure(user, tenant, username, now);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, INVALID_CREDENTIALS);
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "This account is inactive. Contact your administrator.");
        }
        if (user.isAdminLocked()) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED, "This account is locked. Contact your administrator.");
        }
        tenantAccess.assertActive(user.getTenantCode());

        user.clearFailures();
        user.setLastLoginAt(now);
        audit.publish(user.getTenantCode(), user.getId().toString(), user.getDisplayName(), user.getRole(), null,
                "permissions", "Signed in", user.getRole());
        return issueTokens(user, UUID.randomUUID(), ip, userAgent);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public TokenResponse refresh(String rawToken, String ip, String userAgent) {
        Instant now = Instant.now();
        RefreshToken token = refreshTokens.findByTokenHash(TokenService.hash(rawToken))
                .orElseThrow(this::sessionExpired);

        if (token.getRevokedAt() != null) {
            // A used/revoked token was replayed: assume theft and end every session of this sign-in.
            refreshTokens.revokeFamily(token.getFamilyId(), now);
            audit.publish(token.getTenantCode(), token.getUserId().toString(), null, null, null,
                    "permissions", "Refresh token reuse detected", "Session family revoked");
            throw sessionExpired();
        }
        if (!token.getExpiresAt().isAfter(now)) {
            throw sessionExpired();
        }
        UserAccount user = users.findById(token.getUserId()).orElseThrow(this::sessionExpired);
        if (user.getStatus() != UserStatus.ACTIVE || user.isAdminLocked() || !tenantAccess.isActive(user.getTenantCode())) {
            refreshTokens.revokeFamily(token.getFamilyId(), now);
            throw sessionExpired();
        }

        token.setRevokedAt(now);
        return issueTokens(user, token.getFamilyId(), ip, userAgent);
    }

    @Transactional
    public void logout(String rawToken) {
        refreshTokens.findByTokenHash(TokenService.hash(rawToken)).ifPresent(token -> {
            refreshTokens.revokeFamily(token.getFamilyId(), Instant.now());
            audit.publish(token.getTenantCode(), token.getUserId().toString(), null, null, null,
                    "permissions", "Signed out", "");
        });
    }

    @Transactional
    public TokenResponse changePassword(ChangePasswordRequest req, String ip, String userAgent) {
        AuthenticatedUser actor = SecurityUtils.currentUser();
        if (actor.isImpersonated()) {
            throw BusinessException.forbidden("Passwords cannot be changed during a support session.");
        }
        UserAccount user = users.findById(actor.userId())
                .orElseThrow(() -> BusinessException.notFound("User"));
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw BusinessException.validation("Current password is incorrect.", "currentPassword");
        }
        passwordPolicy.validate(req.newPassword(), user.getUsername(), user.getDisplayName());
        if (passwordEncoder.matches(req.newPassword(), user.getPasswordHash())) {
            throw BusinessException.validation("New password must be different from the current one.", "newPassword");
        }
        List<PasswordHistoryEntry> history = passwordHistory.findByUserIdOrderByCreatedAtDesc(user.getId());
        boolean reused = history.stream().limit(PASSWORD_HISTORY)
                .anyMatch(h -> passwordEncoder.matches(req.newPassword(), h.getPasswordHash()));
        if (reused) {
            throw BusinessException.validation("You cannot reuse one of your last " + PASSWORD_HISTORY + " passwords.",
                    "newPassword");
        }

        Instant now = Instant.now();
        passwordHistory.save(new PasswordHistoryEntry(user.getId(), user.getPasswordHash(), now));
        history.stream().skip(PASSWORD_HISTORY - 1L).forEach(passwordHistory::delete);

        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.setMustChangePassword(false);
        user.setPasswordChangedAt(now);
        refreshTokens.revokeAllForUser(user.getId(), now);
        audit.record("permissions", "Changed password", "");
        return issueTokens(user, UUID.randomUUID(), ip, userAgent);
    }

    @Transactional(readOnly = true)
    public UserProfile me() {
        AuthenticatedUser actor = SecurityUtils.currentUser();
        UserAccount user = users.findById(actor.userId()).orElseThrow(() -> BusinessException.notFound("User"));
        return toProfile(user, actor.permissions(), actor.isImpersonated());
    }

    /** Audited, short-lived support session as another user. No refresh token is issued. */
    @Transactional
    public TokenResponse impersonate(UUID targetUserId) {
        AuthenticatedUser actor = SecurityUtils.currentUser();
        if (!actor.isPlatformOwner() || actor.isImpersonated()) {
            throw BusinessException.forbidden("Only the platform owner can open a support session.");
        }
        UserAccount target = users.findById(targetUserId).orElseThrow(() -> BusinessException.notFound("User"));
        if (Role.SSA.name().equals(target.getRole()) || target.getStatus() != UserStatus.ACTIVE) {
            throw BusinessException.rule("This account cannot be used for a support session.");
        }
        Set<String> perms = permissionService.effectivePermissions(target.getTenantCode(), target.getRole());
        Duration ttl = props.jwt().impersonationTtl();
        String access = tokenService.issueAccessToken(target, perms, actor.userId(), ttl);

        audit.publish(Role.PLATFORM_TENANT, actor.userId().toString(), actor.name(), actor.role(), null,
                "superadmins", "Impersonation started", target.getTenantCode() + " / " + target.getUsername());
        audit.publish(target.getTenantCode(), target.getId().toString(), target.getDisplayName(), target.getRole(),
                actor.userId().toString(), "permissions", "Platform owner opened a support session", target.getUsername());
        return new TokenResponse(access, null, "Bearer", ttl.toSeconds(), toProfile(target, perms, true));
    }

    private TokenResponse issueTokens(UserAccount user, UUID familyId, String ip, String userAgent) {
        Set<String> perms = permissionService.effectivePermissions(user.getTenantCode(), user.getRole());
        Duration ttl = props.jwt().accessTokenTtl();
        String access = tokenService.issueAccessToken(user, perms, null, ttl);
        String refresh = tokenService.issueRefreshToken(user, familyId, ip, userAgent);
        return new TokenResponse(access, refresh, "Bearer", ttl.toSeconds(), toProfile(user, perms, false));
    }

    private void registerFailure(UserAccount user, String tenant, String username, Instant now) {
        String auditTenant = SecurityUtils.isValidTenantCode(tenant) ? tenant : Role.PLATFORM_TENANT;
        if (user == null) {
            audit.publish(auditTenant, "anonymous", username.length() > 40 ? username.substring(0, 40) : username,
                    "ANON", null, "permissions", "Failed sign-in", "Unknown username");
            return;
        }
        user.setFailedAttempts(user.getFailedAttempts() + 1);
        AuthProperties.Lockout policy = props.lockout();
        String detail = "Wrong password (attempt " + user.getFailedAttempts() + ")";
        if (user.getFailedAttempts() % policy.maxAttempts() == 0) {
            user.setLockoutCount(user.getLockoutCount() + 1);
            long factor = 1L << Math.min(user.getLockoutCount() - 1, 10);
            Duration lock = policy.baseLock().multipliedBy(factor);
            if (lock.compareTo(policy.maxLock()) > 0) {
                lock = policy.maxLock();
            }
            user.setLockedUntil(now.plus(lock));
            detail += " - locked for " + lock.toMinutes() + " min";
        }
        audit.publish(user.getTenantCode(), user.getId().toString(), user.getDisplayName(), user.getRole(), null,
                "permissions", "Failed sign-in", detail);
    }

    private BusinessException sessionExpired() {
        return new BusinessException(ErrorCode.UNAUTHENTICATED, "Your session has expired. Please sign in again.");
    }

    static UserProfile toProfile(UserAccount user, Set<String> perms, boolean impersonated) {
        return new UserProfile(user.getId(), user.getUsername(), user.getDisplayName(), user.getEmail(), user.getRole(),
                user.getTenantCode(), user.getEmployeeId(), user.getEmployeeCode(), user.getBranchId(),
                user.isMustChangePassword(), impersonated, perms);
    }
}
